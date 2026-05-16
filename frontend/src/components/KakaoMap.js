import React, { useEffect, useRef, useState } from 'react';
import './KakaoMap.css';

// 두 좌표 사이를 N 등분해서 중간점 삽입 (애니메이션용)
function densifyPath(pathCoords, pointsPerSegment = 80) {
  if (pathCoords.length < 2) return pathCoords;
  const result = [];
  for (let i = 0; i < pathCoords.length - 1; i++) {
    const [lng1, lat1] = pathCoords[i];
    const [lng2, lat2] = pathCoords[i + 1];
    for (let j = 0; j < pointsPerSegment; j++) {
      const t = j / pointsPerSegment;
      result.push([lng1 + (lng2 - lng1) * t, lat1 + (lat2 - lat1) * t]);
    }
  }
  result.push(pathCoords[pathCoords.length - 1]);
  return result;
}

// ease-in-out cubic
function easeInOut(t) {
  return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
}

const MODE_STROKE = { WALK: 'shortdash', SUBWAY: 'solid', BUS: 'solid' };
const MODE_LABEL  = { WALK: '도보', SUBWAY: '지하철', BUS: '버스' };

function markerStart() {
  return `<div style="display:flex;flex-direction:column;align-items:center;">
    <div style="background:#22C55E;color:#fff;border-radius:8px;padding:3px 10px;font-size:11px;font-weight:700;white-space:nowrap;box-shadow:0 2px 8px rgba(0,0,0,0.18);font-family:-apple-system,sans-serif;">출발</div>
    <div style="width:10px;height:10px;background:#22C55E;border:2.5px solid white;border-radius:50%;margin-top:2px;box-shadow:0 1px 4px rgba(0,0,0,0.2);"></div>
  </div>`;
}

function markerEnd() {
  return `<div style="display:flex;flex-direction:column;align-items:center;">
    <div style="background:#EF4444;color:#fff;border-radius:8px;padding:3px 10px;font-size:11px;font-weight:700;white-space:nowrap;box-shadow:0 2px 8px rgba(0,0,0,0.18);font-family:-apple-system,sans-serif;">도착</div>
    <div style="width:10px;height:10px;background:#EF4444;border:2.5px solid white;border-radius:50%;margin-top:2px;box-shadow:0 1px 4px rgba(0,0,0,0.2);"></div>
  </div>`;
}

function markerMode(mode) {
  const label = MODE_LABEL[mode] || '';
  return `<div style="background:white;border:1.5px solid #D1D5DB;border-radius:6px;padding:2px 7px;font-size:10px;font-weight:600;color:#6B7280;white-space:nowrap;box-shadow:0 1px 4px rgba(0,0,0,0.12);font-family:-apple-system,sans-serif;">${label}</div>`;
}

function markerTransfer() {
  return `<div style="width:8px;height:8px;background:#9CA3AF;border:2px solid white;border-radius:50%;box-shadow:0 1px 3px rgba(0,0,0,0.2);"></div>`;
}

function markerStop(stopName, distText) {
  return `
    <div style="display:flex;flex-direction:column;align-items:center;cursor:pointer;">
      <div style="background:white;border:2.5px solid #3B82F6;border-radius:8px;padding:4px 10px;font-size:11px;font-weight:600;color:#1F2937;white-space:nowrap;box-shadow:0 2px 8px rgba(0,0,0,0.15);line-height:1.5;font-family:-apple-system,sans-serif;">
        <span style="color:#3B82F6;font-size:10px;margin-right:3px;">●</span>${stopName}${distText ? ' ' + distText : ''}
      </div>
      <div style="width:10px;height:10px;background:#3B82F6;border:2px solid white;border-radius:50%;margin-top:2px;box-shadow:0 1px 4px rgba(0,0,0,0.2);"></div>
    </div>`;
}

function KakaoMap({ center, myLocation, stops, segments }) {
  const mapRef         = useRef(null);
  const mapInstanceRef = useRef(null);   // 카카오 Map 인스턴스 — 마운트 동안 재사용
  const layersRef      = useRef([]);     // 레이어(오버레이/폴리라인/원) — 재그리기 전 정리
  const lastContentKey = useRef(null);   // 동일 콘텐츠 리렌더 스킵
  const [mapReady, setMapReady] = useState(null);

  useEffect(() => {
    let cancelled = false;
    let rafId     = null;

    function initOrRedraw() {
      const kakao = window.kakao;
      if (!kakao || !kakao.maps) { setMapReady(false); return; }

      kakao.maps.load(() => {
        if (cancelled || !mapRef.current) return;

        // 동일 콘텐츠로 인한 부모 리렌더는 스킵 — 사용자의 줌/팬을 유지
        const contentKey = JSON.stringify({
          segs: segments?.map(s => [s.mode, s.path?.length ?? 0, s.path?.[0]?.[0], s.path?.[0]?.[1]]) ?? null,
          stops: stops?.map(s => [s.stopName, s.coordinates?.lat, s.coordinates?.lng]) ?? null,
          my: [myLocation.lat, myLocation.lng],
        });
        if (contentKey === lastContentKey.current && mapInstanceRef.current) {
          return;
        }
        lastContentKey.current = contentKey;

        // 인스턴스: 한 번만 생성, 이후 재사용
        let map = mapInstanceRef.current;
        if (!map) {
          map = new kakao.maps.Map(mapRef.current, {
            center: new kakao.maps.LatLng(center.lat, center.lng),
            level: 5,
          });
          mapInstanceRef.current = map;
        }

        // 이전 레이어 정리 (인스턴스는 유지하고 그 위 오버레이/폴리라인/원만 제거)
        layersRef.current.forEach(l => { try { l.setMap?.(null); } catch (_) {} });
        layersRef.current = [];

        const bounds = new kakao.maps.LatLngBounds();

        // 내 위치 점
        const myLL = new kakao.maps.LatLng(myLocation.lat, myLocation.lng);
        const c1 = new kakao.maps.Circle({ map, center: myLL, radius: 14, strokeWeight: 0, fillColor: '#fff',    fillOpacity: 1 });
        const c2 = new kakao.maps.Circle({ map, center: myLL, radius:  9, strokeWeight: 0, fillColor: '#3B82F6', fillOpacity: 1 });
        layersRef.current.push(c1, c2);
        bounds.extend(myLL);

        // ── 경로 애니메이션 ──────────────────────────────────────────
        if (segments && segments.length > 0) {

          // 세그먼트별 촘촘한 좌표 배열
          const densePaths = segments.map(seg => densifyPath(seg.path, 80));
          densePaths.forEach(path =>
            path.forEach(([lng, lat]) => bounds.extend(new kakao.maps.LatLng(lat, lng)))
          );

          // 출발 마커
          const fp = densePaths[0][0];
          const startOv = new kakao.maps.CustomOverlay({ map, yAnchor: 1.2, xAnchor: 0.5,
            position: new kakao.maps.LatLng(fp[1], fp[0]),
            content: markerStart() });
          layersRef.current.push(startOv);

          // 도착 마커
          const lp = densePaths[densePaths.length - 1];
          const ep = lp[lp.length - 1];
          const endOv = new kakao.maps.CustomOverlay({ map, yAnchor: 1.2, xAnchor: 0.5,
            position: new kakao.maps.LatLng(ep[1], ep[0]),
            content: markerEnd() });
          layersRef.current.push(endOv);


          // 환승 지점 회색 점 (세그먼트 경계, 첫/끝 제외)
          for (let i = 1; i < densePaths.length; i++) {
            const [lng, lat] = densePaths[i][0];
            const tOv = new kakao.maps.CustomOverlay({ map, yAnchor: 0.5, xAnchor: 0.5,
              position: new kakao.maps.LatLng(lat, lng),
              content: markerTransfer() });
            layersRef.current.push(tOv);
          }

          // 세그먼트별 Polyline (처음엔 빈 경로)
          const polylines = segments.map(seg => {
            const pl = new kakao.maps.Polyline({
              map,
              path: [],
              strokeWeight: 4,
              strokeColor: '#3B82F6',
              strokeOpacity: 0.88,
              strokeStyle: MODE_STROKE[seg.mode] || 'solid',
            });
            layersRef.current.push(pl);
            return pl;
          });

          // 이동 점 오버레이
          const dotDiv = document.createElement('div');
          dotDiv.style.cssText = [
            'width:12px', 'height:12px', 'border-radius:50%',
            'background:#3B82F6', 'border:2.5px solid white',
            'box-shadow:0 0 10px rgba(59,130,246,0.9)',
            'transform:translate(-50%,-50%)',
          ].join(';');
          const dotOverlay = new kakao.maps.CustomOverlay({ content: dotDiv, zIndex: 10 });
          layersRef.current.push(dotOverlay);

          const allDense  = densePaths.flat();
          const totalPts  = allDense.length;
          const DRAW_DUR  = 2000;
          const MOVE_DUR  = 3000;
          let startTime   = null;
          let phase       = 'drawing';

          function animate(ts) {
            if (cancelled) return;

            if (phase === 'drawing') {
              if (!startTime) startTime = ts;
              const raw     = Math.min((ts - startTime) / DRAW_DUR, 1);
              const target  = Math.floor(easeInOut(raw) * totalPts);

              let drawn = 0;
              for (let si = 0; si < densePaths.length; si++) {
                if (drawn >= target) break;
                const take = Math.min(target - drawn, densePaths[si].length);
                polylines[si].setPath(
                  densePaths[si].slice(0, take).map(([lng, lat]) =>
                    new kakao.maps.LatLng(lat, lng))
                );
                drawn += take;
              }

              if (raw < 1) {
                rafId = requestAnimationFrame(animate);
              } else {
                phase     = 'moving';
                startTime = null;
                dotOverlay.setMap(map);
                rafId = requestAnimationFrame(animate);
              }

            } else {
              if (!startTime) startTime = ts;
              const loopP = ((ts - startTime) % MOVE_DUR) / MOVE_DUR;
              const idx   = Math.min(Math.floor(loopP * totalPts), totalPts - 1);
              const [lng, lat] = allDense[idx];
              dotOverlay.setPosition(new kakao.maps.LatLng(lat, lng));
              rafId = requestAnimationFrame(animate);
            }
          }

          rafId = requestAnimationFrame(animate);

        } else {
          // ── 세그먼트 없을 때: 기존 정류장 마커 ────────────────────
          stops.forEach(stop => {
            const { lat, lng } = stop.coordinates;
            const distText = stop.walkingDistanceMeters != null
              ? `<span style="color:#6B7280;font-size:10px;font-weight:500;">${stop.walkingDistanceMeters}m</span>`
              : '';
            const pos = new kakao.maps.LatLng(lat, lng);
            bounds.extend(pos);
            const ov = new kakao.maps.CustomOverlay({
              map, position: pos,
              content: markerStop(stop.stopName, distText),
              yAnchor: 1, xAnchor: 0.5,
            });
            layersRef.current.push(ov);
          });
        }

        if (!cancelled) {
          // 콘텐츠가 실제로 바뀐 경우에만 카메라 fit — 동일 콘텐츠 리렌더에서는 위에서 일찍 return 됨
          map.setBounds(bounds, 70, 70, 70, 70);
          setMapReady(true);
        }
      });
    }

    if (window.kakao && window.kakao.maps) {
      initOrRedraw();
    } else {
      const timer   = setInterval(() => {
        if (window.kakao && window.kakao.maps) { clearInterval(timer); initOrRedraw(); }
      }, 100);
      const timeout = setTimeout(() => {
        clearInterval(timer);
        if (!cancelled) setMapReady(false);
      }, 5000);
      return () => {
        cancelled = true;
        clearInterval(timer);
        clearTimeout(timeout);
        if (rafId) cancelAnimationFrame(rafId);
      };
    }

    return () => { cancelled = true; if (rafId) cancelAnimationFrame(rafId); };
  }, [center, myLocation, stops, segments]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="kakao-map-wrapper">
      <div ref={mapRef} className="kakao-map" />

      {mapReady === null && (
        <div className="kakao-map-loading">
          <div className="kakao-map-loading__spinner" />
          <span>지도를 불러오는 중...</span>
        </div>
      )}

      {mapReady === false && (
        <div className="kakao-map-placeholder">
          <div className="kakao-map-placeholder__inner">
            <svg width="40" height="40" viewBox="0 0 24 24" fill="none" className="kakao-map-placeholder__icon">
              <path d="M12 2C8.13 2 5 5.13 5 9c0 5.25 7 13 7 13s7-7.75 7-13c0-3.87-3.13-7-7-7z" fill="#E5E7EB"/>
              <circle cx="12" cy="9" r="2.5" fill="#9CA3AF"/>
            </svg>
            <p className="kakao-map-placeholder__text">카카오맵을 불러올 수 없어요</p>
            <p className="kakao-map-placeholder__sub">API 키 또는 네트워크를 확인해주세요</p>
            <div className="kakao-map-mock">
              {(stops || []).map((stop, i) => (
                <div key={stop.stopName} className="kakao-map-mock__marker"
                  style={{ top: `${40 + i * 15}%`, left: `${45 + i * 10}%` }}>
                  <div className="kakao-map-mock__bubble" style={{ borderColor: '#3B82F6' }}>
                    <span style={{ color: '#3B82F6' }}>●</span> {stop.stopName}
                  </div>
                  <div className="kakao-map-mock__dot" style={{ background: '#3B82F6' }} />
                </div>
              ))}
              <div className="kakao-map-mock__myloc">
                <div className="kakao-map-mock__myloc-ring" />
                <div className="kakao-map-mock__myloc-dot" />
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default KakaoMap;
