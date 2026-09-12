'use client';
import React from 'react';
import PageHeader from '@/components/ui/PageHeader';
import { Card, CardContent, CardHeader } from '@/components/ui/Card';
import Input from '@/components/ui/Input';
import Button from '@/components/ui/Button';
import { useToast } from '@/components/ui/ToastProvider';

export default function SettingsPage() {
  const { toast } = useToast();

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    toast('success', 'Settings saved successfully');
  };

  return (
    <div className="max-w-4xl">
      <PageHeader title="Settings" subtitle="System configuration" />
      <div className="space-y-6">
        <Card>
          <CardHeader><h3 className="font-semibold text-gray-800 dark:text-gray-200">Business Profile</h3></CardHeader>
          <CardContent>
            <form onSubmit={handleSave} className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <Input label="Company Name" defaultValue="StockFlow Trading PLC" />
                <Input label="Tax ID / TIN" defaultValue="0012345678" />
              </div>
              <Input label="Address" defaultValue="Addis Ababa, Ethiopia" />
              <div className="grid grid-cols-2 gap-4">
                <Input label="Phone" defaultValue="+251 11 123 4567" />
                <Input label="Email" defaultValue="contact@stockflow.com" />
              </div>
              <div className="pt-2"><Button type="submit">Save Changes</Button></div>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
