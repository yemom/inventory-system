'use client';
import React, { useEffect, useState } from 'react';
import PageHeader from '@/components/ui/PageHeader';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Input from '@/components/ui/Input';
import Button from '@/components/ui/Button';
import { useToast } from '@/components/ui/ToastProvider';

const STORAGE_KEY = 'stockflow_display_settings';

type DisplaySettings = {
  companyName: string;
  taxId: string;
  address: string;
  phone: string;
  email: string;
};

const defaults = (): DisplaySettings => ({
  companyName: process.env.NEXT_PUBLIC_APP_NAME || 'StockFlow',
  taxId: '',
  address: '',
  phone: '',
  email: '',
});

function loadSettings(): DisplaySettings {
  if (typeof window === 'undefined') return defaults();
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return defaults();
    return { ...defaults(), ...JSON.parse(raw) };
  } catch {
    return defaults();
  }
}

export default function SettingsPage() {
  const { toast } = useToast();
  const [form, setForm] = useState<DisplaySettings>(defaults);
  const [loaded, setLoaded] = useState(false);

  useEffect(() => {
    setForm(loadSettings());
    setLoaded(true);
  }, []);

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    localStorage.setItem(STORAGE_KEY, JSON.stringify(form));
    toast(
      'success',
      'Display preferences saved',
      'Stored in this browser only. No backend settings API is available yet.'
    );
  };

  return (
    <div className="max-w-4xl">
      <PageHeader title="Settings" subtitle="Display preferences (local only — no settings API)" />
      <div className="space-y-6">
        <Card>
          <CardHeader>
            <h3 className="font-semibold text-gray-800 dark:text-gray-200">Business Profile</h3>
            <p className="text-xs text-gray-500 mt-1">
              Company name defaults from <code className="text-[11px]">NEXT_PUBLIC_APP_NAME</code>.
              Changes are saved to localStorage for display on this device — they are not written to the database.
            </p>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleSave} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <Input
                  label="Company Name"
                  value={form.companyName}
                  onChange={e => setForm(f => ({ ...f, companyName: e.target.value }))}
                  disabled={!loaded}
                />
                <Input
                  label="Tax ID / TIN"
                  value={form.taxId}
                  onChange={e => setForm(f => ({ ...f, taxId: e.target.value }))}
                  disabled={!loaded}
                  placeholder="Optional"
                />
              </div>
              <Input
                label="Address"
                value={form.address}
                onChange={e => setForm(f => ({ ...f, address: e.target.value }))}
                disabled={!loaded}
                placeholder="Optional"
              />
              <div className="grid grid-cols-2 gap-4">
                <Input
                  label="Phone"
                  value={form.phone}
                  onChange={e => setForm(f => ({ ...f, phone: e.target.value }))}
                  disabled={!loaded}
                  placeholder="Optional"
                />
                <Input
                  label="Email"
                  type="email"
                  value={form.email}
                  onChange={e => setForm(f => ({ ...f, email: e.target.value }))}
                  disabled={!loaded}
                  placeholder="Optional"
                />
              </div>
              <div className="pt-2 flex items-center gap-3">
                <Button type="submit" disabled={!loaded}>Save to this browser</Button>
                <span className="text-xs text-gray-400">Not persisted to the server</span>
              </div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
