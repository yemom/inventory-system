// Shared mock data types and generators
export interface Product {
  id: string; name: string; sku: string; category: string; unit: string;
  costPrice: number; sellingPrice: number; quantity: number; reorderLevel: number;
  status: 'active' | 'inactive' | 'discontinued'; createdAt: string;
}

export interface Customer {
  id: string; name: string; phone: string; email: string; address: string;
  creditLimit: number; balance: number; totalPurchases: number;
  status: 'active' | 'inactive'; createdAt: string;
}

export interface Supplier {
  id: string; name: string; contact: string; phone: string; email: string;
  address: string; balance: number; totalOrders: number;
  status: 'active' | 'inactive'; createdAt: string;
}

export interface SaleItem {
  productId: string; productName: string; quantity: number;
  unitPrice: number; discount: number; total: number;
}

export interface SaleOrder {
  id: string; reference: string; customerId: string; customerName: string;
  date: string; status: 'draft' | 'confirmed' | 'delivered' | 'cancelled';
  paymentStatus: 'unpaid' | 'partial' | 'paid';
  subtotal: number; discount: number; tax: number; total: number; paid: number;
  items: SaleItem[];
}

export interface PurchaseItem {
  productId: string; productName: string; quantity: number; unitCost: number; total: number;
}

export interface PurchaseOrder {
  id: string; reference: string; supplierId: string; supplierName: string;
  date: string; status: 'draft' | 'ordered' | 'received' | 'cancelled';
  paymentStatus: 'unpaid' | 'partial' | 'paid';
  subtotal: number; tax: number; total: number; paid: number;
  items: PurchaseItem[];
}

export interface StockMovement {
  id: string; date: string; type: 'sale' | 'purchase' | 'transfer' | 'adjustment' | 'return';
  productId: string; productName: string; quantity: number;
  direction: 'in' | 'out'; reference: string; note?: string;
}

export interface Transfer {
  id: string; reference: string; fromLocation: string; toLocation: string;
  date: string; status: 'draft' | 'in_transit' | 'completed' | 'cancelled';
  items: { productId: string; productName: string; quantity: number }[];
}

export interface Payment {
  id: string; reference: string; type: 'received' | 'made';
  amount: number; date: string; method: 'cash' | 'bank' | 'mobile';
  party: string; partyType: 'customer' | 'supplier'; note?: string;
}

export interface Expense {
  id: string; category: string; description: string;
  amount: number; date: string; paidBy: string; note?: string;
}

export interface User {
  id: string; username: string; fullName: string; email: string;
  role: 'ADMIN' | 'MANAGER' | 'STOREKEEPER' | 'CASHIER' | 'ACCOUNTANT';
  status: 'active' | 'inactive'; lastLogin: string; createdAt: string;
}

export interface AuditLog {
  id: string; userId: string; username: string; action: string;
  resource: string; resourceId?: string; ip: string; timestamp: string; details?: string;
}

export const delay = (ms: number) => new Promise<void>(r => setTimeout(r, ms));

// ── Mock Data ──────────────────────────────────────────────────────────────────

export const products: Product[] = [
  { id: 'p1', name: 'Basmati Rice 25kg', sku: 'RICE-BAS-25', category: 'Grains', unit: 'bag', costPrice: 1200, sellingPrice: 1450, quantity: 142, reorderLevel: 20, status: 'active', createdAt: '2024-01-10' },
  { id: 'p2', name: 'Wheat Flour 50kg', sku: 'FLOUR-WHT-50', category: 'Grains', unit: 'bag', costPrice: 890, sellingPrice: 1100, quantity: 85, reorderLevel: 15, status: 'active', createdAt: '2024-01-10' },
  { id: 'p3', name: 'Cooking Oil 20L', sku: 'OIL-COOK-20', category: 'Oil & Fat', unit: 'jerry', costPrice: 750, sellingPrice: 920, quantity: 63, reorderLevel: 10, status: 'active', createdAt: '2024-01-12' },
  { id: 'p4', name: 'Sugar 50kg', sku: 'SUGAR-WHT-50', category: 'Sweeteners', unit: 'bag', costPrice: 1100, sellingPrice: 1300, quantity: 48, reorderLevel: 10, status: 'active', createdAt: '2024-01-12' },
  { id: 'p5', name: 'Salt 1kg', sku: 'SALT-TBL-1', category: 'Condiments', unit: 'pack', costPrice: 18, sellingPrice: 25, quantity: 320, reorderLevel: 50, status: 'active', createdAt: '2024-01-15' },
  { id: 'p6', name: 'Tomato Paste 500g', sku: 'TOM-PAS-500', category: 'Canned Goods', unit: 'can', costPrice: 45, sellingPrice: 65, quantity: 180, reorderLevel: 30, status: 'active', createdAt: '2024-01-15' },
  { id: 'p7', name: 'Instant Noodles', sku: 'NOOD-INST-P', category: 'Dry Goods', unit: 'packet', costPrice: 22, sellingPrice: 30, quantity: 12, reorderLevel: 50, status: 'active', createdAt: '2024-01-20' },
  { id: 'p8', name: 'Laundry Detergent 2kg', sku: 'DET-LAUN-2', category: 'Household', unit: 'pack', costPrice: 185, sellingPrice: 230, quantity: 95, reorderLevel: 20, status: 'active', createdAt: '2024-01-22' },
  { id: 'p9', name: 'Biscuits Assorted', sku: 'BISC-ASST-B', category: 'Snacks', unit: 'box', costPrice: 320, sellingPrice: 420, quantity: 0, reorderLevel: 15, status: 'active', createdAt: '2024-02-01' },
  { id: 'p10', name: 'Canned Tuna 185g', sku: 'TUNA-CAN-185', category: 'Canned Goods', unit: 'can', costPrice: 78, sellingPrice: 100, quantity: 245, reorderLevel: 40, status: 'active', createdAt: '2024-02-01' },
  { id: 'p11', name: 'Coffee 500g', sku: 'COFF-GRN-500', category: 'Beverages', unit: 'pack', costPrice: 650, sellingPrice: 820, quantity: 38, reorderLevel: 10, status: 'active', createdAt: '2024-02-05' },
  { id: 'p12', name: 'Tea Bags 100pcs', sku: 'TEA-BAG-100', category: 'Beverages', unit: 'box', costPrice: 120, sellingPrice: 155, quantity: 76, reorderLevel: 15, status: 'active', createdAt: '2024-02-05' },
  { id: 'p13', name: 'Baby Diapers M (50pcs)', sku: 'DIAP-MED-50', category: 'Baby Care', unit: 'pack', costPrice: 420, sellingPrice: 540, quantity: 32, reorderLevel: 10, status: 'active', createdAt: '2024-02-10' },
  { id: 'p14', name: 'Soap Bar (3pcs)', sku: 'SOAP-BAR-3', category: 'Personal Care', unit: 'pack', costPrice: 55, sellingPrice: 75, quantity: 150, reorderLevel: 30, status: 'active', createdAt: '2024-02-10' },
  { id: 'p15', name: 'Pasta 500g', sku: 'PAST-SPG-500', category: 'Dry Goods', unit: 'pack', costPrice: 38, sellingPrice: 55, quantity: 8, reorderLevel: 25, status: 'inactive', createdAt: '2024-02-15' },
  { id: 'p16', name: 'Honey 500ml', sku: 'HONY-NAT-500', category: 'Sweeteners', unit: 'bottle', costPrice: 280, sellingPrice: 380, quantity: 28, reorderLevel: 8, status: 'active', createdAt: '2024-02-20' },
  { id: 'p17', name: 'Olive Oil 1L', sku: 'OIL-OLIV-1L', category: 'Oil & Fat', unit: 'bottle', costPrice: 420, sellingPrice: 550, quantity: 45, reorderLevel: 12, status: 'active', createdAt: '2024-02-20' },
  { id: 'p18', name: 'Black Pepper 100g', sku: 'PEPP-BLK-100', category: 'Spices', unit: 'pack', costPrice: 68, sellingPrice: 95, quantity: 88, reorderLevel: 20, status: 'active', createdAt: '2024-03-01' },
  { id: 'p19', name: 'Milk Powder 500g', sku: 'MILK-PWD-500', category: 'Dairy', unit: 'pack', costPrice: 380, sellingPrice: 480, quantity: 54, reorderLevel: 15, status: 'active', createdAt: '2024-03-01' },
  { id: 'p20', name: 'Matches 10pcs', sku: 'MATCH-BOX-10', category: 'Household', unit: 'bundle', costPrice: 8, sellingPrice: 12, quantity: 500, reorderLevel: 100, status: 'active', createdAt: '2024-03-05' },
];

export const customers: Customer[] = [
  { id: 'c1', name: 'Abebe Kebede', phone: '0911234567', email: 'abebe@example.com', address: 'Addis Ababa, Bole', creditLimit: 50000, balance: 8500, totalPurchases: 145000, status: 'active', createdAt: '2024-01-05' },
  { id: 'c2', name: 'Tigist Haile', phone: '0922345678', email: 'tigist@example.com', address: 'Addis Ababa, Kirkos', creditLimit: 30000, balance: 0, totalPurchases: 88000, status: 'active', createdAt: '2024-01-08' },
  { id: 'c3', name: 'Yohannes Tesfaye', phone: '0933456789', email: 'yohannes@example.com', address: 'Addis Ababa, Arada', creditLimit: 80000, balance: 22000, totalPurchases: 320000, status: 'active', createdAt: '2024-01-15' },
  { id: 'c4', name: 'Mekdes Alemu', phone: '0944567890', email: 'mekdes@example.com', address: 'Addis Ababa, Yeka', creditLimit: 20000, balance: 0, totalPurchases: 45000, status: 'active', createdAt: '2024-02-01' },
  { id: 'c5', name: 'Solomon Bekele', phone: '0955678901', email: 'solomon@example.com', address: 'Addis Ababa, Gulele', creditLimit: 100000, balance: 35000, totalPurchases: 580000, status: 'active', createdAt: '2024-02-10' },
  { id: 'c6', name: 'Selamawit Tadesse', phone: '0966789012', email: 'selamawit@example.com', address: 'Addis Ababa, Nifas Silk', creditLimit: 25000, balance: 7500, totalPurchases: 78000, status: 'active', createdAt: '2024-02-15' },
  { id: 'c7', name: 'Dawit Girma', phone: '0977890123', email: 'dawit@example.com', address: 'Addis Ababa, Kolfe', creditLimit: 15000, balance: 0, totalPurchases: 32000, status: 'inactive', createdAt: '2024-02-20' },
  { id: 'c8', name: 'Hana Mulatu', phone: '0988901234', email: 'hana@example.com', address: 'Addis Ababa, Lideta', creditLimit: 40000, balance: 12000, totalPurchases: 195000, status: 'active', createdAt: '2024-03-01' },
  { id: 'c9', name: 'Bereket Worku', phone: '0999012345', email: 'bereket@example.com', address: 'Addis Ababa, Akaki', creditLimit: 60000, balance: 0, totalPurchases: 250000, status: 'active', createdAt: '2024-03-05' },
  { id: 'c10', name: 'Genet Assefa', phone: '0910123456', email: 'genet@example.com', address: 'Addis Ababa, Bole', creditLimit: 35000, balance: 5000, totalPurchases: 110000, status: 'active', createdAt: '2024-03-10' },
];

export const suppliers: Supplier[] = [
  { id: 's1', name: 'Ethio Trading PLC', contact: 'Kebede Alemu', phone: '0111234567', email: 'info@ethiotrading.com', address: 'Addis Ababa, Merkato', balance: 45000, totalOrders: 25, status: 'active', createdAt: '2024-01-01' },
  { id: 's2', name: 'Dire Dawa Suppliers', contact: 'Ahmed Mohammed', phone: '0252345678', email: 'sales@ddireda.com', address: 'Dire Dawa', balance: 0, totalOrders: 18, status: 'active', createdAt: '2024-01-05' },
  { id: 's3', name: 'Nazreth General Trade', contact: 'Tigist Bekele', phone: '0223456789', email: 'info@nazrethtrade.com', address: 'Adama', balance: 28000, totalOrders: 12, status: 'active', createdAt: '2024-01-10' },
  { id: 's4', name: 'Addis Ababa FMCG Dist.', contact: 'Dawit Haile', phone: '0114567890', email: 'orders@aafmcg.com', address: 'Addis Ababa, Kaliti', balance: 0, totalOrders: 32, status: 'active', createdAt: '2024-01-15' },
  { id: 's5', name: 'Global Imports Ethiopia', contact: 'Sara Tesfaye', phone: '0115678901', email: 'import@globaleth.com', address: 'Addis Ababa, Bole', balance: 120000, totalOrders: 8, status: 'active', createdAt: '2024-02-01' },
  { id: 's6', name: 'Hawassa Agro Suppliers', contact: 'Lemma Worku', phone: '0466789012', email: 'agro@hawassa.com', address: 'Hawassa', balance: 15000, totalOrders: 7, status: 'inactive', createdAt: '2024-02-10' },
];

export const saleOrders: SaleOrder[] = [
  { id: 'so1', reference: 'SO-2024-001', customerId: 'c1', customerName: 'Abebe Kebede', date: '2024-09-01', status: 'delivered', paymentStatus: 'paid', subtotal: 8700, discount: 200, tax: 0, total: 8500, paid: 8500, items: [{ productId: 'p1', productName: 'Basmati Rice 25kg', quantity: 3, unitPrice: 1450, discount: 0, total: 4350 }, { productId: 'p3', productName: 'Cooking Oil 20L', quantity: 2, unitPrice: 920, discount: 200, total: 1640 }, { productId: 'p4', productName: 'Sugar 50kg', quantity: 2, unitPrice: 1300, discount: 0, total: 2600 }] },
  { id: 'so2', reference: 'SO-2024-002', customerId: 'c3', customerName: 'Yohannes Tesfaye', date: '2024-09-02', status: 'confirmed', paymentStatus: 'partial', subtotal: 15600, discount: 0, tax: 0, total: 15600, paid: 8000, items: [{ productId: 'p1', productName: 'Basmati Rice 25kg', quantity: 5, unitPrice: 1450, discount: 0, total: 7250 }, { productId: 'p2', productName: 'Wheat Flour 50kg', quantity: 4, unitPrice: 1100, discount: 0, total: 4400 }] },
  { id: 'so3', reference: 'SO-2024-003', customerId: 'c5', customerName: 'Solomon Bekele', date: '2024-09-03', status: 'delivered', paymentStatus: 'paid', subtotal: 23000, discount: 500, tax: 0, total: 22500, paid: 22500, items: [{ productId: 'p1', productName: 'Basmati Rice 25kg', quantity: 10, unitPrice: 1450, discount: 500, total: 14000 }, { productId: 'p3', productName: 'Cooking Oil 20L', quantity: 5, unitPrice: 920, discount: 0, total: 4600 }] },
  { id: 'so4', reference: 'SO-2024-004', customerId: 'c2', customerName: 'Tigist Haile', date: '2024-09-04', status: 'draft', paymentStatus: 'unpaid', subtotal: 4550, discount: 0, tax: 0, total: 4550, paid: 0, items: [{ productId: 'p5', productName: 'Salt 1kg', quantity: 10, unitPrice: 25, discount: 0, total: 250 }, { productId: 'p10', productName: 'Canned Tuna 185g', quantity: 30, unitPrice: 100, discount: 0, total: 3000 }] },
  { id: 'so5', reference: 'SO-2024-005', customerId: 'c8', customerName: 'Hana Mulatu', date: '2024-09-05', status: 'delivered', paymentStatus: 'partial', subtotal: 12500, discount: 0, tax: 0, total: 12500, paid: 500, items: [{ productId: 'p11', productName: 'Coffee 500g', quantity: 5, unitPrice: 820, discount: 0, total: 4100 }, { productId: 'p13', productName: 'Baby Diapers M (50pcs)', quantity: 10, unitPrice: 540, discount: 0, total: 5400 }] },
];

export const purchaseOrders: PurchaseOrder[] = [
  { id: 'po1', reference: 'PO-2024-001', supplierId: 's1', supplierName: 'Ethio Trading PLC', date: '2024-08-28', status: 'received', paymentStatus: 'paid', subtotal: 45000, tax: 0, total: 45000, paid: 45000, items: [{ productId: 'p1', productName: 'Basmati Rice 25kg', quantity: 20, unitCost: 1200, total: 24000 }, { productId: 'p4', productName: 'Sugar 50kg', quantity: 15, unitCost: 1100, total: 16500 }] },
  { id: 'po2', reference: 'PO-2024-002', supplierId: 's4', supplierName: 'Addis Ababa FMCG Dist.', date: '2024-09-01', status: 'ordered', paymentStatus: 'partial', subtotal: 32000, tax: 0, total: 32000, paid: 16000, items: [{ productId: 'p8', productName: 'Laundry Detergent 2kg', quantity: 50, unitCost: 185, total: 9250 }, { productId: 'p10', productName: 'Canned Tuna 185g', quantity: 200, unitCost: 78, total: 15600 }] },
  { id: 'po3', reference: 'PO-2024-003', supplierId: 's5', supplierName: 'Global Imports Ethiopia', date: '2024-09-05', status: 'draft', paymentStatus: 'unpaid', subtotal: 84000, tax: 0, total: 84000, paid: 0, items: [{ productId: 'p17', productName: 'Olive Oil 1L', quantity: 100, unitCost: 420, total: 42000 }, { productId: 'p11', productName: 'Coffee 500g', quantity: 50, unitCost: 650, total: 32500 }] },
];

export const payments: Payment[] = [
  { id: 'pay1', reference: 'PAY-001', type: 'received', amount: 8500, date: '2024-09-01', method: 'cash', party: 'Abebe Kebede', partyType: 'customer' },
  { id: 'pay2', reference: 'PAY-002', type: 'received', amount: 8000, date: '2024-09-02', method: 'bank', party: 'Yohannes Tesfaye', partyType: 'customer' },
  { id: 'pay3', reference: 'PAY-003', type: 'made', amount: 45000, date: '2024-08-30', method: 'bank', party: 'Ethio Trading PLC', partyType: 'supplier' },
  { id: 'pay4', reference: 'PAY-004', type: 'received', amount: 22500, date: '2024-09-03', method: 'mobile', party: 'Solomon Bekele', partyType: 'customer' },
  { id: 'pay5', reference: 'PAY-005', type: 'made', amount: 16000, date: '2024-09-04', method: 'bank', party: 'Addis Ababa FMCG Dist.', partyType: 'supplier' },
  { id: 'pay6', reference: 'PAY-006', type: 'received', amount: 500, date: '2024-09-05', method: 'cash', party: 'Hana Mulatu', partyType: 'customer' },
];

export const expenses: Expense[] = [
  { id: 'exp1', category: 'Rent', description: 'Monthly warehouse rent', amount: 15000, date: '2024-09-01', paidBy: 'admin' },
  { id: 'exp2', category: 'Utilities', description: 'Electricity bill', amount: 3500, date: '2024-09-02', paidBy: 'admin' },
  { id: 'exp3', category: 'Salaries', description: 'Staff salaries - September', amount: 45000, date: '2024-09-05', paidBy: 'admin' },
  { id: 'exp4', category: 'Transport', description: 'Delivery vehicle fuel', amount: 2800, date: '2024-09-06', paidBy: 'manager' },
  { id: 'exp5', category: 'Maintenance', description: 'Generator maintenance', amount: 1200, date: '2024-09-07', paidBy: 'admin' },
  { id: 'exp6', category: 'Office Supplies', description: 'Printer paper and ink', amount: 850, date: '2024-09-08', paidBy: 'manager' },
  { id: 'exp7', category: 'Marketing', description: 'Social media ads', amount: 5000, date: '2024-09-09', paidBy: 'admin' },
  { id: 'exp8', category: 'Insurance', description: 'Annual insurance premium', amount: 8000, date: '2024-09-10', paidBy: 'admin' },
];

export const users: User[] = [
  { id: 'u1', username: 'admin', fullName: 'Tesfaye Bekele', email: 'admin@stockflow.com', role: 'ADMIN', status: 'active', lastLogin: '2024-09-12', createdAt: '2024-01-01' },
  { id: 'u2', username: 'manager', fullName: 'Almaz Girma', email: 'manager@stockflow.com', role: 'MANAGER', status: 'active', lastLogin: '2024-09-11', createdAt: '2024-01-05' },
  { id: 'u3', username: 'storekeeper', fullName: 'Kebede Tadesse', email: 'store@stockflow.com', role: 'STOREKEEPER', status: 'active', lastLogin: '2024-09-12', createdAt: '2024-01-10' },
  { id: 'u4', username: 'cashier', fullName: 'Tigist Alemu', email: 'cashier@stockflow.com', role: 'CASHIER', status: 'active', lastLogin: '2024-09-12', createdAt: '2024-01-15' },
  { id: 'u5', username: 'accountant', fullName: 'Dawit Haile', email: 'accounts@stockflow.com', role: 'ACCOUNTANT', status: 'active', lastLogin: '2024-09-10', createdAt: '2024-01-20' },
  { id: 'u6', username: 'cashier2', fullName: 'Sara Worku', email: 'cashier2@stockflow.com', role: 'CASHIER', status: 'inactive', lastLogin: '2024-08-25', createdAt: '2024-02-01' },
];

export const auditLogs: AuditLog[] = [
  { id: 'al1', userId: 'u1', username: 'admin', action: 'LOGIN', resource: 'Auth', ip: '192.168.1.100', timestamp: '2024-09-12T08:00:00Z' },
  { id: 'al2', userId: 'u4', username: 'cashier', action: 'CREATE', resource: 'SaleOrder', resourceId: 'so5', ip: '192.168.1.104', timestamp: '2024-09-12T08:30:00Z', details: 'Created SO-2024-005' },
  { id: 'al3', userId: 'u3', username: 'storekeeper', action: 'UPDATE', resource: 'Product', resourceId: 'p7', ip: '192.168.1.103', timestamp: '2024-09-12T09:00:00Z', details: 'Adjusted stock for Instant Noodles' },
  { id: 'al4', userId: 'u1', username: 'admin', action: 'CREATE', resource: 'User', resourceId: 'u6', ip: '192.168.1.100', timestamp: '2024-09-12T09:30:00Z', details: 'Created user cashier2' },
  { id: 'al5', userId: 'u2', username: 'manager', action: 'APPROVE', resource: 'PurchaseOrder', resourceId: 'po2', ip: '192.168.1.102', timestamp: '2024-09-12T10:00:00Z', details: 'Approved PO-2024-002' },
  { id: 'al6', userId: 'u4', username: 'cashier', action: 'CREATE', resource: 'Payment', resourceId: 'pay6', ip: '192.168.1.104', timestamp: '2024-09-12T10:30:00Z', details: 'Recorded payment PAY-006' },
  { id: 'al7', userId: 'u5', username: 'accountant', action: 'CREATE', resource: 'Expense', resourceId: 'exp7', ip: '192.168.1.105', timestamp: '2024-09-12T11:00:00Z', details: 'Recorded Marketing expense' },
  { id: 'al8', userId: 'u1', username: 'admin', action: 'DELETE', resource: 'Product', resourceId: 'p15', ip: '192.168.1.100', timestamp: '2024-09-12T11:30:00Z', details: 'Deactivated Pasta 500g' },
];

export const stockMovements: StockMovement[] = [
  { id: 'sm1', date: '2024-09-01', type: 'sale', productId: 'p1', productName: 'Basmati Rice 25kg', quantity: 3, direction: 'out', reference: 'SO-2024-001' },
  { id: 'sm2', date: '2024-09-01', type: 'sale', productId: 'p3', productName: 'Cooking Oil 20L', quantity: 2, direction: 'out', reference: 'SO-2024-001' },
  { id: 'sm3', date: '2024-08-28', type: 'purchase', productId: 'p1', productName: 'Basmati Rice 25kg', quantity: 20, direction: 'in', reference: 'PO-2024-001' },
  { id: 'sm4', date: '2024-08-28', type: 'purchase', productId: 'p4', productName: 'Sugar 50kg', quantity: 15, direction: 'in', reference: 'PO-2024-001' },
  { id: 'sm5', date: '2024-09-03', type: 'adjustment', productId: 'p7', productName: 'Instant Noodles', quantity: 5, direction: 'out', reference: 'ADJ-001', note: 'Damaged goods' },
  { id: 'sm6', date: '2024-09-05', type: 'sale', productId: 'p11', productName: 'Coffee 500g', quantity: 5, direction: 'out', reference: 'SO-2024-005' },
  { id: 'sm7', date: '2024-09-06', type: 'transfer', productId: 'p5', productName: 'Salt 1kg', quantity: 50, direction: 'out', reference: 'TR-001', note: 'Transfer to Branch 2' },
  { id: 'sm8', date: '2024-09-07', type: 'purchase', productId: 'p8', productName: 'Laundry Detergent 2kg', quantity: 30, direction: 'in', reference: 'PO-2024-002' },
  { id: 'sm9', date: '2024-09-08', type: 'return', productId: 'p2', productName: 'Wheat Flour 50kg', quantity: 2, direction: 'in', reference: 'RET-001', note: 'Customer return' },
  { id: 'sm10', date: '2024-09-09', type: 'sale', productId: 'p13', productName: 'Baby Diapers M (50pcs)', quantity: 10, direction: 'out', reference: 'SO-2024-005' },
];

export const transfers: Transfer[] = [
  { id: 'tr1', reference: 'TR-001', fromLocation: 'Main Warehouse', toLocation: 'Branch 2', date: '2024-09-06', status: 'completed', items: [{ productId: 'p5', productName: 'Salt 1kg', quantity: 50 }] },
  { id: 'tr2', reference: 'TR-002', fromLocation: 'Main Warehouse', toLocation: 'Branch 1', date: '2024-09-08', status: 'in_transit', items: [{ productId: 'p6', productName: 'Tomato Paste 500g', quantity: 30 }, { productId: 'p10', productName: 'Canned Tuna 185g', quantity: 50 }] },
  { id: 'tr3', reference: 'TR-003', fromLocation: 'Branch 1', toLocation: 'Main Warehouse', date: '2024-09-10', status: 'draft', items: [{ productId: 'p14', productName: 'Soap Bar (3pcs)', quantity: 20 }] },
];

export interface Category {
  id: string;
  name: string;
  description: string;
  parentCategory?: string;
  productCount: number;
  status: 'active' | 'inactive';
}

export const categories: Category[] = [
  { id: 'cat1', name: 'Grains & Flours', description: 'Rice, wheat, corn, and ground staples', productCount: 2, status: 'active' },
  { id: 'cat2', name: 'Oil & Fat', description: 'Cooking oil, vegetable oil, butter', productCount: 2, status: 'active' },
  { id: 'cat3', name: 'Sweeteners', description: 'White sugar, brown sugar, honey', productCount: 2, status: 'active' },
  { id: 'cat4', name: 'Beverages', description: 'Coffee, tea, water, soft drinks', productCount: 2, status: 'active' },
  { id: 'cat5', name: 'Canned Goods', description: 'Fish, tomato, beans, fruit preserves', productCount: 2, status: 'active' },
  { id: 'cat6', name: 'Household & Cleaning', description: 'Detergents, soaps, disinfectants', productCount: 2, status: 'active' },
  { id: 'cat7', name: 'Spices & Condiments', description: 'Salt, pepper, seasoning blends', productCount: 2, status: 'active' },
  { id: 'cat8', name: 'Snacks & Confectionery', description: 'Biscuits, chips, chocolates', productCount: 1, status: 'active' },
];

export interface Warehouse {
  id: string;
  code: string;
  name: string;
  location: string;
  manager: string;
  phone: string;
  capacityUtilization: number;
  totalProducts: number;
  status: 'active' | 'inactive';
}

export const warehouses: Warehouse[] = [
  { id: 'wh1', code: 'WH-MAIN', name: 'Main Central Warehouse', location: 'Addis Ababa, Kaliti Industrial Zone', manager: 'Kebede Tadesse', phone: '0911554433', capacityUtilization: 78, totalProducts: 18, status: 'active' },
  { id: 'wh2', code: 'WH-BR1', name: 'Branch 1 - Merkato Depot', location: 'Addis Ababa, Merkato', manager: 'Almaz Girma', phone: '0922667788', capacityUtilization: 62, totalProducts: 14, status: 'active' },
  { id: 'wh3', code: 'WH-BR2', name: 'Branch 2 - Bole Distribution', location: 'Addis Ababa, Bole Medhanialem', manager: 'Dawit Haile', phone: '0933889900', capacityUtilization: 45, totalProducts: 10, status: 'active' },
  { id: 'wh4', code: 'WH-RET', name: 'Retail Store Front', location: 'Addis Ababa, Piassa Commercial Center', manager: 'Tigist Alemu', phone: '0944112233', capacityUtilization: 88, totalProducts: 16, status: 'active' },
];

export interface OrderReturn {
  id: string;
  reference: string;
  originalReference: string;
  partyName: string;
  type: 'customer_return' | 'supplier_return';
  date: string;
  amount: number;
  status: 'completed' | 'pending' | 'rejected';
  reason: string;
  items: { productId: string; productName: string; quantity: number; unitPrice: number; total: number }[];
}

export const orderReturns: OrderReturn[] = [
  { id: 'ret1', reference: 'SRET-2024-001', originalReference: 'SO-2024-001', partyName: 'Abebe Kebede', type: 'customer_return', date: '2024-09-04', amount: 1450, status: 'completed', reason: 'Packaging damaged during transport', items: [{ productId: 'p1', productName: 'Basmati Rice 25kg', quantity: 1, unitPrice: 1450, total: 1450 }] },
  { id: 'ret2', reference: 'PRET-2024-001', originalReference: 'PO-2024-002', partyName: 'Addis Ababa FMCG Dist.', type: 'supplier_return', date: '2024-09-05', amount: 925, status: 'completed', reason: 'Quality did not match specifications', items: [{ productId: 'p8', productName: 'Laundry Detergent 2kg', quantity: 5, unitPrice: 185, total: 925 }] },
  { id: 'ret3', reference: 'SRET-2024-002', originalReference: 'SO-2024-003', partyName: 'Solomon Bekele', type: 'customer_return', date: '2024-09-08', amount: 920, status: 'pending', reason: 'Excess quantity ordered by client', items: [{ productId: 'p3', productName: 'Cooking Oil 20L', quantity: 1, unitPrice: 920, total: 920 }] },
];

