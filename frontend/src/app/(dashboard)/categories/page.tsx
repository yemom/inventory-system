"use client";

import React, { useState, useEffect, useCallback } from "react";

import { Plus, Edit, Trash2, Tag, RefreshCw } from "lucide-react";

import PageHeader from "@/components/ui/PageHeader";
import DataTable, { Column } from "@/components/ui/DataTable";

import Button from "@/components/ui/Button";
import Modal from "@/components/ui/Modal";
import Input from "@/components/ui/Input";
import Select from "@/components/ui/Select";
import ConfirmDialog from "@/components/ui/ConfirmDialog";

import { categoriesApi, Category } from "@/lib/api/categoriesApi";

import { useToast } from "@/components/ui/ToastProvider";

/**
 * ============================================================
 * CATEGORIES PAGE
 * ============================================================
 */

export default function CategoriesPage() {
  const { toast } = useToast();

  /**
   * ----------------------------------------------------------
   * STATE
   * ----------------------------------------------------------
   */

  const [categories, setCategories] = useState<Category[]>([]);

  const [loading, setLoading] = useState(true);

  const [saving, setSaving] = useState(false);

  const [modalOpen, setModalOpen] = useState(false);

  const [editing, setEditing] = useState<Category | null>(null);

  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null);

  const [deleting, setDeleting] = useState(false);

  const [name, setName] = useState("");

  const [description, setDescription] = useState("");

  const [parentId, setParentId] = useState("");

  /**
   * ==========================================================
   * FETCH CATEGORIES
   * ==========================================================
   */

  const fetchCategories = useCallback(async () => {
    try {
      setLoading(true);

      const data = await categoriesApi.list();

      /**
       * Always make sure state receives an array.
       */
      setCategories(Array.isArray(data) ? data : []);
    } catch (error: unknown) {
      console.error("Failed to fetch categories:", error);

      const message =
        error instanceof Error ? error.message : "Unable to load categories";

      toast("error", "Failed to fetch categories", message);

      setCategories([]);
    } finally {
      setLoading(false);
    }
  }, [toast]);

  /**
   * ==========================================================
   * INITIAL LOAD
   * ==========================================================
   */

  useEffect(() => {
    fetchCategories();
  }, [fetchCategories]);

  /**
   * ==========================================================
   * CREATE CATEGORY MODAL
   * ==========================================================
   */

  const openCreate = () => {
    setEditing(null);

    setName("");

    setDescription("");

    setParentId("");

    setModalOpen(true);
  };

  /**
   * ==========================================================
   * EDIT CATEGORY MODAL
   * ==========================================================
   */

  const openEdit = (category: Category) => {
    setEditing(category);

    setName(category.name || "");

    setDescription(category.description || "");

    setParentId(category.parentId != null ? String(category.parentId) : "");

    setModalOpen(true);
  };

  /**
   * ==========================================================
   * CLOSE MODAL
   * ==========================================================
   */

  const closeModal = () => {
    if (saving) {
      return;
    }

    setModalOpen(false);

    setEditing(null);

    setName("");

    setDescription("");

    setParentId("");
  };

  /**
   * ==========================================================
   * SAVE CATEGORY
   * ==========================================================
   */

  const handleSave = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();

    const trimmedName = name.trim();

    const trimmedDescription = description.trim();

    /**
     * Client-side validation.
     */

    if (!trimmedName) {
      toast("error", "Invalid category", "Category name is required.");

      return;
    }

    try {
      setSaving(true);

      const payload = {
        name: trimmedName,

        description: trimmedDescription || undefined,

        parentId: parentId ? Number(parentId) : null,
      };

      /**
       * ------------------------------------------------------
       * UPDATE
       * ------------------------------------------------------
       */

      if (editing) {
        await categoriesApi.update(editing.id, payload);

        toast(
          "success",
          "Category updated",
          `${trimmedName} was updated successfully.`,
        );
      } else {
        /**
         * ------------------------------------------------------
         * CREATE
         * ------------------------------------------------------
         */
        const createdCategory = await categoriesApi.create(payload);

        console.log("Created category:", createdCategory);

        toast(
          "success",
          "Category created",
          `${trimmedName} was added successfully.`,
        );
      }

      /**
       * Close modal.
       */

      closeModal();

      /**
       * IMPORTANT:
       *
       * Fetch from PostgreSQL again instead of only
       * modifying local state.
       *
       * This guarantees that the UI displays the
       * persisted database data.
       */

      await fetchCategories();
    } catch (error: unknown) {
      console.error("Category save error:", error);

      let message = "Unable to save category.";

      if (error && typeof error === "object" && "response" in error) {
        const axiosError = error as {
          response?: {
            data?: {
              message?: string;
            };
          };
        };

        message = axiosError.response?.data?.message || message;
      }

      if (error instanceof Error && error.message) {
        message = error.message;
      }

      toast("error", "Save failed", message);
    } finally {
      setSaving(false);
    }
  };

  /**
   * ==========================================================
   * DELETE CATEGORY
   * ==========================================================
   */

  const confirmDelete = async () => {
    if (!deleteTarget) {
      return;
    }

    try {
      setDeleting(true);

      await categoriesApi.delete(deleteTarget.id);

      toast(
        "success",
        "Category deleted",
        `${deleteTarget.name} was removed successfully.`,
      );

      setDeleteTarget(null);

      /**
       * Reload actual database data.
       */

      await fetchCategories();
    } catch (error: unknown) {
      console.error("Delete category error:", error);

      let message = "Unable to delete category.";

      if (error && typeof error === "object" && "response" in error) {
        const axiosError = error as {
          response?: {
            data?: {
              message?: string;
            };
          };
        };

        message = axiosError.response?.data?.message || message;
      }

      if (error instanceof Error && error.message) {
        message = error.message;
      }

      toast("error", "Delete failed", message);
    } finally {
      setDeleting(false);
    }
  };

  /**
   * ==========================================================
   * TABLE COLUMNS
   * ==========================================================
   */

  const columns: Column<Category>[] = [
    {
      key: "name",

      label: "Category Name",

      render: (_value, row) => {
        const parent =
          row.parentId != null
            ? categories.find(
                (category) => Number(category.id) === Number(row.parentId),
              )
            : undefined;

        return (
          <div className="flex items-center gap-2.5">
            <div
              className="
                flex
                h-8
                w-8
                items-center
                justify-center
                rounded-lg
                bg-blue-50
                text-blue-600
                dark:bg-blue-900/30
                dark:text-blue-400
              "
            >
              <Tag size={15} />
            </div>

            <div>
              <span
                className="
                  font-semibold
                  text-gray-900
                  dark:text-gray-100
                "
              >
                {row.name}
              </span>

              {parent && (
                <span
                  className="
                    block
                    text-[11px]
                    text-gray-400
                  "
                >
                  Subcategory of: {parent.name}
                </span>
              )}
            </div>
          </div>
        );
      },
    },

    {
      key: "description",

      label: "Description",

      render: (value) => (
        <span
          className="
            text-xs
            text-gray-500
          "
        >
          {value ? String(value) : "-"}
        </span>
      ),
    },

    {
      key: "id",

      label: "Subcategories",

      render: (_value, row) => {
        const count = Array.isArray(row.subCategories)
          ? row.subCategories.length
          : 0;

        return (
          <span
            className="
              rounded
              bg-gray-100
              px-2
              py-0.5
              font-mono
              text-xs
              dark:bg-gray-700
            "
          >
            {count} items
          </span>
        );
      },
    },
  ];

  /**
   * ==========================================================
   * PARENT CATEGORY OPTIONS
   * ==========================================================
   */

  const parentOptions = [
    {
      value: "",
      label: "None (Top-level Category)",
    },

    ...categories

      .filter(
        (category) => !editing || Number(category.id) !== Number(editing.id),
      )

      .map((category) => ({
        value: String(category.id),

        label: category.name,
      })),
  ];

  /**
   * ==========================================================
   * RENDER
   * ==========================================================
   */

  return (
    <div className="space-y-4">
      {/* ====================================================
          PAGE HEADER
          ==================================================== */}

      <PageHeader
        title="Categories"
        subtitle="
          Organize your store inventory hierarchy
        "
        actions={
          <div className="flex gap-2">
            <Button
              type="button"
              variant="outline"
              onClick={fetchCategories}
              disabled={loading}
            >
              <RefreshCw size={15} className={loading ? "animate-spin" : ""} />
              Refresh
            </Button>

            <Button type="button" onClick={openCreate}>
              <Plus size={15} />
              Add Category
            </Button>
          </div>
        }
      />

      {/* ====================================================
          CATEGORY TABLE
          ==================================================== */}

      <DataTable
        columns={columns as Column<any>[]}
        data={categories as unknown as Record<string, unknown>[]}
        searchable
        searchPlaceholder="
          Search product categories...
        "
        loading={loading}
        actions={(row) => (
          <div
            className="
              flex
              justify-end
              gap-1
            "
          >
            <button
              type="button"
              onClick={() => openEdit(row as unknown as Category)}
              className="
                rounded
                p-1.5
                text-gray-500
                hover:bg-gray-100
                hover:text-blue-600
              "
            >
              <Edit size={14} />
            </button>

            <button
              type="button"
              onClick={() => setDeleteTarget(row as unknown as Category)}
              className="
                rounded
                p-1.5
                text-gray-500
                hover:bg-gray-100
                hover:text-red-600
              "
            >
              <Trash2 size={14} />
            </button>
          </div>
        )}
      />

      {/* ====================================================
          CREATE / EDIT MODAL
          ==================================================== */}

      <Modal
        open={modalOpen}
        onClose={closeModal}
        title={editing ? "Edit Category" : "Create Category"}
      >
        <form onSubmit={handleSave} className="space-y-4">
          <Input
            label="Category Name"
            placeholder="
              e.g. Dairy & Eggs
            "
            value={name}
            onChange={(event) => setName(event.target.value)}
            required
            disabled={saving}
          />

          <Input
            label="Description"
            placeholder="
              Brief description of product line
            "
            value={description}
            onChange={(event) => setDescription(event.target.value)}
            disabled={saving}
          />

          <Select
            label="
              Parent Category
              (Optional for nesting)
            "
            options={parentOptions}
            value={parentId}
            onChange={(event) => setParentId(event.target.value)}
            disabled={saving}
          />

          <div
            className="
              flex
              justify-end
              gap-3
              pt-2
            "
          >
            <Button
              type="button"
              variant="outline"
              onClick={closeModal}
              disabled={saving}
            >
              Cancel
            </Button>

            <Button type="submit" disabled={saving}>
              {saving
                ? "Saving..."
                : editing
                  ? "Update Category"
                  : "Save Category"}
            </Button>
          </div>
        </form>
      </Modal>

      {/* ====================================================
          DELETE CONFIRMATION
          ==================================================== */}

      <ConfirmDialog
        open={!!deleteTarget}
        onClose={() => {
          if (!deleting) {
            setDeleteTarget(null);
          }
        }}
        onConfirm={confirmDelete}
        title="Delete Category"
        message={
          deleteTarget
            ? `Are you sure you want to delete "${deleteTarget.name}"? Products assigned to this category may need to be recategorized.`
            : "Are you sure you want to delete this category?"
        }
      />
    </div>
  );
}
