"use client";

import React, { useState, useEffect, useCallback } from "react";

import {
  Plus,
  Edit,
  Warehouse as WarehouseIcon,
  MapPin,
  Phone,
  User,
} from "lucide-react";

import PageHeader from "@/components/ui/PageHeader";
import DataTable, { Column } from "@/components/ui/DataTable";
import Badge from "@/components/ui/Badge";
import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import Input from "@/components/ui/Input";

import {
  warehousesApi,
  Warehouse,
  CreateWarehouseRequest,
} from "@/lib/api/warehousesApi";

import { useToast } from "@/components/ui/ToastProvider";

/**
 * DataTable requires its generic type to extend
 * Record<string, unknown>.
 *
 * Your API Warehouse interface should NOT be changed just
 * to satisfy the table component.
 *
 * This type is only used by DataTable.
 */
type WarehouseTableRow = Record<string, unknown> & {
  id: number;
  name: string;
  location?: string;
  managerName?: string;
  contactPhone?: string;
  active: boolean;
  createdAt?: string;
};

export default function WarehousesPage() {
  const { toast } = useToast();

  const [warehouseList, setWarehouseList] = useState<Warehouse[]>([]);

  const [loading, setLoading] = useState(true);

  const [modalOpen, setModalOpen] = useState(false);

  const [editing, setEditing] = useState<Warehouse | null>(null);

  const [name, setName] = useState("");
  const [location, setLocation] = useState("");
  const [managerName, setManagerName] = useState("");
  const [contactPhone, setContactPhone] = useState("");

  const [saving, setSaving] = useState(false);

  /**
   * Fetch warehouses from backend.
   */
  const fetchWarehouses = useCallback(async () => {
    try {
      setLoading(true);

      const data = await warehousesApi.list();

      setWarehouseList(data);
    } catch (error: unknown) {
      const err = error as {
        response?: {
          data?: {
            message?: string;
            error?: string;
          };
        };
        message?: string;
      };

      toast(
        "error",
        "Fetch Error",
        err.response?.data?.message ??
          err.response?.data?.error ??
          err.message ??
          "Error loading warehouses",
      );
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    void fetchWarehouses();
  }, [fetchWarehouses]);

  /**
   * Reset form.
   */
  const resetForm = () => {
    setName("");
    setLocation("");
    setManagerName("");
    setContactPhone("");
    setEditing(null);
  };

  /**
   * Open create modal.
   */
  const openCreate = () => {
    resetForm();
    setModalOpen(true);
  };

  /**
   * Open edit modal.
   */
  const openEdit = (warehouse: Warehouse) => {
    setEditing(warehouse);

    setName(warehouse.name ?? "");
    setLocation(warehouse.location ?? "");
    setManagerName(warehouse.managerName ?? "");
    setContactPhone(warehouse.contactPhone ?? "");

    setModalOpen(true);
  };

  /**
   * Close modal.
   */
  const handleClose = () => {
    if (saving) {
      return;
    }

    setModalOpen(false);
    resetForm();
  };

  /**
   * Create or update warehouse.
   */
  const handleSave = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const trimmedName = name.trim();

    if (!trimmedName) {
      toast("warning", "Name required", "Warehouse name is required.");
      return;
    }

    const payload: CreateWarehouseRequest = {
      name: trimmedName,
      location: location.trim() || undefined,
      managerName: managerName.trim() || undefined,
      contactPhone: contactPhone.trim() || undefined,
    };

    try {
      setSaving(true);

      if (editing) {
        await warehousesApi.update(editing.id, payload);

        toast(
          "success",
          "Warehouse updated",
          `${trimmedName} was updated successfully.`,
        );
      } else {
        await warehousesApi.create(payload);

        toast(
          "success",
          "Warehouse created",
          `${trimmedName} was registered successfully.`,
        );
      }

      setModalOpen(false);

      resetForm();

      await fetchWarehouses();
    } catch (error: unknown) {
      const err = error as {
        response?: {
          data?: {
            message?: string;
            error?: string;
          };
        };
        message?: string;
      };

      toast(
        "error",
        "Save Failed",
        err.response?.data?.message ??
          err.response?.data?.error ??
          err.message ??
          "Unable to save warehouse.",
      );
    } finally {
      setSaving(false);
    }
  };

  /**
   * Convert API Warehouse objects into rows accepted
   * by the generic DataTable.
   *
   * We keep the original Warehouse objects untouched.
   */
  const tableData: WarehouseTableRow[] = warehouseList.map((warehouse) => ({
    id: warehouse.id,
    name: warehouse.name,
    location: warehouse.location,
    managerName: warehouse.managerName,
    contactPhone: warehouse.contactPhone,
    active: warehouse.active,
    createdAt: warehouse.createdAt,
  }));

  /**
   * DataTable columns.
   */
  const columns: Column<WarehouseTableRow>[] = [
    {
      key: "name",
      label: "Warehouse / Store",

      render: (_, row) => (
        <div className="flex items-center gap-2.5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-emerald-50 text-emerald-600 dark:bg-emerald-900/30 dark:text-emerald-400">
            <WarehouseIcon size={16} />
          </div>

          <span className="font-semibold text-gray-900 dark:text-gray-100">
            {row.name}
          </span>
        </div>
      ),
    },

    {
      key: "location",
      label: "Location",

      render: (value) => {
        const locationValue =
          typeof value === "string"
            ? value
            : value != null
              ? String(value)
              : "";

        if (!locationValue) {
          return "—";
        }

        return (
          <span className="flex items-center gap-1 text-xs text-gray-600 dark:text-gray-300">
            <MapPin size={12} className="text-gray-400" />

            {locationValue}
          </span>
        );
      },
    },

    {
      key: "managerName",
      label: "Supervisor",

      render: (_, row) => {
        const hasManager = Boolean(row.managerName);

        const hasPhone = Boolean(row.contactPhone);

        if (!hasManager && !hasPhone) {
          return "—";
        }

        return (
          <div className="text-xs">
            {hasManager && (
              <p className="flex items-center gap-1 font-medium text-gray-800 dark:text-gray-200">
                <User size={12} className="text-gray-400" />

                {row.managerName}
              </p>
            )}

            {hasPhone && (
              <p className="mt-0.5 flex items-center gap-1 text-gray-400">
                <Phone size={11} />

                {row.contactPhone}
              </p>
            )}
          </div>
        );
      },
    },

    {
      key: "active",
      label: "Status",

      render: (value) => {
        const isActive = Boolean(value);

        return (
          <Badge variant={isActive ? "success" : "default"}>
            {isActive ? "Active" : "Inactive"}
          </Badge>
        );
      },
    },
  ];

  return (
    <div className="space-y-4">
      <PageHeader
        title="Warehouses"
        subtitle="Manage physical depots, branches, retail store fronts, and inventory locations"
        actions={
          <Button onClick={openCreate}>
            <Plus size={15} />
            Add Warehouse
          </Button>
        }
      />

      <DataTable<WarehouseTableRow>
        columns={columns}
        data={tableData}
        loading={loading}
        searchable
        searchPlaceholder="Search locations..."
        actions={(row) => (
          <div className="flex justify-end gap-1">
            <button
              type="button"
              onClick={() => {
                const warehouse = warehouseList.find(
                  (item) => item.id === row.id,
                );

                if (warehouse) {
                  openEdit(warehouse);
                }
              }}
              className="rounded p-1.5 text-gray-500 hover:bg-gray-100 hover:text-blue-600"
              title="Edit warehouse"
            >
              <Edit size={14} />
            </button>
          </div>
        )}
      />

      <Modal
        open={modalOpen}
        onClose={handleClose}
        title={editing ? "Edit Warehouse" : "Create Warehouse"}
      >
        <form onSubmit={handleSave} className="space-y-4">
          <Input
            label="Name"
            placeholder="e.g. Main Distribution Center"
            value={name}
            onChange={(event) => setName(event.target.value)}
            required
          />

          <Input
            label="Location"
            placeholder="Address or City"
            value={location}
            onChange={(event) => setLocation(event.target.value)}
          />

          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
            <Input
              label="Manager Name"
              placeholder="Contact person"
              value={managerName}
              onChange={(event) => setManagerName(event.target.value)}
            />

            <Input
              label="Phone Number"
              placeholder="+251..."
              value={contactPhone}
              onChange={(event) => setContactPhone(event.target.value)}
            />
          </div>

          <div className="flex justify-end gap-3 pt-2">
            <Button
              type="button"
              variant="outline"
              onClick={handleClose}
              disabled={saving}
            >
              Cancel
            </Button>

            <Button type="submit" loading={saving} disabled={saving}>
              {editing ? "Update Warehouse" : "Save Warehouse"}
            </Button>
          </div>
        </form>
      </Modal>
    </div>
  );
}
