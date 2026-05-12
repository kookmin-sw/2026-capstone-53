import React, { createContext, useContext, useState } from 'react';
import { mockSettings } from '../data/mockData';

const SettingsContext = createContext(null);

export function SettingsProvider({ children }) {
  const [settings, setSettings] = useState({ ...mockSettings });

  const updateSetting = (key, value) =>
    setSettings(prev => ({ ...prev, [key]: value }));

  return (
    <SettingsContext.Provider value={{ settings, updateSetting }}>
      {children}
    </SettingsContext.Provider>
  );
}

export function useSettings() {
  return useContext(SettingsContext);
}
