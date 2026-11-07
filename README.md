# StockFlow

### AI-Integrated Inventory Management and Business Transaction System

<p align="center">
  <strong>A smarter way to manage inventory, sales, purchasing, and business performance.</strong>
</p>

<p align="center">
  <a href="https://github.com/yemom/inventory-system">GitHub Repository</a> ·
  <a href="https://esrom12-portfolio.vercel.app/">Developer Portfolio</a>
</p>

---

## 1. Project Overview

**StockFlow** is a full-stack inventory management and business transaction platform designed to help medium-sized stores and businesses manage their daily operations through one centralized system.

In many businesses, inventory records, sales transactions, purchasing activities, financial information, and reporting are handled using separate tools or manual processes. This can lead to inaccurate stock records, inconsistent financial information, limited visibility into business performance, and difficulty making informed decisions.

StockFlow addresses these challenges by bringing essential business operations together in a single application.

The platform enables authorized users to manage products, warehouses, suppliers, customers, purchases, sales, payments, expenses, stock movements, and financial reports. It also includes a read-only business intelligence layer that analyzes historical business records to generate forecasts, detect unusual activity, recommend inventory actions, and explain business performance.

Rather than treating inventory as a simple quantity stored against a product, StockFlow uses a transaction-based approach. Stock changes are recorded through a centralized movement ledger, financial calculations are performed by backend services, and sensitive operations are protected through role-based and permission-based authorization.

The goal is to give business owners and employees a clearer, more reliable picture of what is happening across their operations.

## 2. Key Features

### Inventory and Warehouse Management

- Centralized product and category management.
- SKU and barcode-based product identification.
- Multiple warehouse support.
- Warehouse-specific stock quantities and inventory valuation.
- Stock receiving, issuing, adjustments, and transfers.
- Stock movement history and audit records.
- Low-stock and out-of-stock identification.
- Opening stock management.
- Damaged, expired, lost, and shrinkage movement tracking.
- Approval workflows for sensitive stock adjustments and counts.

### Sales and Point of Sale

- Product search and category filtering.
- Shopping cart and quantity management.
- Customer selection.
- Discounts and tax calculations.
- Cash, bank, and mobile payment methods.
- Persistent sales transaction records.
- Payment status and outstanding balance tracking.
- Receipt generation and printing.
- Sales history and reporting.
- Cashier-specific sales visibility.
- Approval workflows for sale voids and refunds.

### Purchasing and Supplier Management

- Supplier information management.
- Purchase order creation and management.
- Goods receiving and inventory updates.
- Purchase item and quantity tracking.
- Supplier balances and payment records.
- Purchase returns and approval workflows.
- Tracking of goods received versus goods still outstanding.

### Customer Management

- Customer profiles and contact information.
- Customer types and account information.
- Credit limits and outstanding balances.
- Customer-associated sales.
- Customer return records.
- Customer support through the business chatbot.

### Financial Management

- Centralized financial calculations.
- Sales revenue and sales return tracking.
- Cost of Goods Sold (COGS).
- Gross profit and net profit.
- Operating expenses.
- Cash inflows and outflows.
- Accounts receivable and accounts payable.
- Inventory valuation.
- Stock loss and shrinkage valuation.
- Financial summaries and detailed reports.

### Reporting and Business Intelligence

- Fourteen documented business reports.
- Date-range filtering.
- Server-generated CSV and PDF exports.
- Sales and purchasing performance analysis.
- Inventory valuation and low-stock reports.
- Profit and loss reporting.
- Sales and product demand forecasting.
- Statistical anomaly detection.
- Inventory optimization recommendations.
- Financial explanations based on recorded transactions.
- Model evaluation and historical query logging.

### Business Assistant and Chatbot

- Natural-language questions about business information.
- Answers generated from trusted backend calculations.
- Staff and customer conversation support.
- Permission-aware business queries.
- Customer-specific order information.
- Conversation history and retention controls.
- Optional OpenAI-compatible response phrasing.
- Deterministic fallback when an external provider is unavailable.

### Security and Access Control

- JWT-based authentication.
- BCrypt password hashing.
- Role-based access control (RBAC).
- Fine-grained permission-based authorization.
- Backend-enforced access restrictions.
- Protected financial and operational endpoints.
- Audit logging for important business operations.
- Rate limiting and request validation.
- Secure environment-based configuration.

---

## 3. Technology Stack

StockFlow uses a modern full-stack architecture with a Java backend, a Next.js frontend, and PostgreSQL for persistent business data.

| Layer                          | Technology                     | Purpose                                       |
| ------------------------------ | ------------------------------ | --------------------------------------------- |
| Backend language               | Java 17                        | Business logic and API development            |
| Backend framework              | Spring Boot 3.2.3              | REST API and application services             |
| Security                       | Spring Security                | Authentication and authorization              |
| Authentication                 | JWT                            | Stateless authentication                      |
| Password security              | BCrypt                         | Secure password hashing                       |
| ORM                            | Spring Data JPA / Hibernate    | Database access and persistence               |
| Database                       | PostgreSQL                     | Transactional business data                   |
| Caching                        | Redis                          | Caching and distributed rate-limiting support |
| Frontend framework             | Next.js                        | Web application                               |
| UI library                     | React                          | Component-based interface                     |
| Frontend language              | TypeScript                     | Type-safe development                         |
| Styling                        | Tailwind CSS                   | Responsive user interface                     |
| API communication              | Axios                          | HTTP requests                                 |
| Server state                   | TanStack React Query           | API data fetching and caching                 |
| Client state                   | Zustand                        | Application state management                  |
| Form handling                  | React Hook Form                | Form management                               |
| Validation                     | Zod                            | Data validation                               |
| Charts                         | Recharts                       | Business data visualization                   |
| Backend testing                | JUnit, Mockito, Testcontainers | Unit and integration testing                  |
| Frontend testing               | Jest, React Testing Library    | Component and application testing             |
| Containerization               | Docker, Docker Compose         | Development and deployment                    |
| Backend hosting configuration  | Render                         | Backend deployment                            |
| Frontend hosting configuration | Vercel                         | Frontend deployment                           |

---

## 4. System Architecture

StockFlow follows a layered client-server architecture. The frontend communicates with the backend through REST APIs, while the backend manages authentication, authorization, business rules, transaction processing, and database operations.

```text
                  STOCKFLOW APPLICATION
                           |
             +-------------+-------------+
             |                           |
             v                           v
      NEXT.JS FRONTEND              SPRING BOOT API
             |                           |
       React Components             REST Controllers
             |                           |
       React Query / Zustand        Spring Security
             |                           |
       Axios API Client             JWT Authentication
             |                           |
             +---------- HTTPS ----------+
                                         |
                                         v
                                  Business Services
                                         |
                      +------------------+------------------+
                      |                  |                  |
                      v                  v                  v
                Inventory /         Finance /          Intelligence /
                Warehouse           Transactions       Chatbot
                      |                  |                  |
                      +------------------+------------------+
                                         |
                                         v
                                  Repository Layer
                                         |
                                         v
                                    PostgreSQL
                                         |
                                         v
                                  Persistent Records

                             Redis
                    Caching / Rate Limiting
```

### Backend Architecture

The backend uses a layered architecture to separate responsibilities and make the application easier to maintain and extend.

```text
HTTP Request
     |
     v
JWT Authentication Filter
     |
     v
Spring Security
     |
     v
Permission Validation
     |
     v
Controller
     |
     v
Service Layer
     |
     v
Repository Layer
     |
     v
PostgreSQL
```

Each layer has a specific responsibility:

- **Controller:** Receives HTTP requests and returns API responses.
- **Service:** Implements business rules, transaction processing, and validations.
- **Repository:** Handles database operations.
- **Entity:** Represents persistent business information.
- **DTO:** Defines structured data exchanged through the API.
- **Security:** Handles authentication, permissions, and access restrictions.

This separation prevents business logic from being scattered across controllers and frontend components.

---

## 5. User Roles and Permissions

StockFlow uses role-based access control combined with fine-grained permissions.

Each user receives access according to their assigned role and the permissions associated with that role. The backend enforces these permissions, so hiding a menu item in the frontend is never treated as the actual security boundary.

### Supported roles

| Role            | Main responsibilities                                                            |
| --------------- | -------------------------------------------------------------------------------- |
| Super Admin     | Complete system administration and access to all permissions.                    |
| Manager         | Manages operational staff, products, inventory, and business operations.         |
| Supervisor      | Supervises operational activities and approves authorized requests.              |
| Cashier         | Handles customer sales, POS operations, and personal sales records.              |
| Inventory Staff | Handles inventory operations, stock receiving, and authorized stock adjustments. |

### Role-specific restrictions

- A Manager can create Supervisors, Cashiers, and Inventory Staff, but cannot create another Manager or a Super Admin.
- A Supervisor can approve authorized stock adjustments, counts, sale voids, and refunds.
- A Supervisor does not automatically receive access to profit and loss or inventory valuation.
- A Cashier can access their own sales but cannot automatically view other employees' sales or business-wide financial reports.
- Inventory Staff cannot create sales or access restricted sales and financial reports.
- Super Admin permissions are resolved as full access.
- Legacy Accountant accounts are retained for compatibility but Accountant is not part of the currently assignable five-role model.
- STOREKEEPER is a deprecated alias for INVENTORY_STAFF.

### Approval workflows

Some business operations require a second authorized user to review and approve them.

For example:

```text
Inventory Staff
      |
      v
Submit Stock Adjustment
      |
      v
Pending Approval
      |
      v
Supervisor / Manager
      |
      +------------------+
      |                  |
      v                  v
   Approve             Reject
      |                  |
      v                  v
Update Stock       Stock Unchanged
```

A similar approval process applies to authorized sale voids and refunds.

These workflows help reduce unauthorized changes and improve accountability.

For the exact permission matrix, see [docs/roles-and-permissions.md](docs/roles-and-permissions.md).

---

## 6. Core Business Modules

### 6.1 Product and Category Management

Products are the foundation of the inventory system.

Product information includes:

- Product name and description.
- SKU and barcode.
- Category and unit.
- Purchase price and selling price.
- Minimum selling price.
- Reorder and stock-level settings.
- Active or inactive status.
- Stock information.

Categories support parent-child relationships, allowing businesses to organize products into meaningful groups.

Example:

```text
Electronics
    |
    +-- Computers
    |      |
    |      +-- Laptops
    |      +-- Desktop Computers
    |
    +-- Accessories
           |
           +-- Keyboards
           +-- Mouse
```

Products can be searched by name, SKU, or barcode. Product deactivation is used instead of physical deletion through the product delete operation.

### 6.2 Multi-Warehouse Inventory

StockFlow supports inventory management across multiple warehouses.

The authoritative stock balance is stored per warehouse in `WarehouseStock`, rather than relying exclusively on one quantity field on the product.

The stock movement process is centralized through `StockLedgerService`.

```text
Stock Operation
      |
      v
StockLedgerService
      |
      v
WarehouseStock Update
      |
      v
Stock Movement Record
      |
      v
Updated Inventory Balance
```

Supported stock operations include:

- Receiving stock.
- Issuing stock.
- Warehouse transfers.
- Stock adjustments.
- Stock counts.
- Sales-related stock deductions.
- Returns.
- Damage, expiry, loss, and shrinkage.

A warehouse transfer records both the outgoing and incoming movement under a shared movement group.

This makes it possible to trace where inventory came from and where it went.

### 6.3 Sales and Point of Sale

The POS module supports the checkout process, including product selection, cart management, customer selection, discounts, payment methods, and receipt generation.

A completed sale is represented by persistent backend transaction records rather than being treated only as temporary frontend data.

The general workflow is:

```text
Search Product
      |
      v
Add to Cart
      |
      v
Select Customer
      |
      v
Choose Warehouse
      |
      v
Apply Discount and Tax
      |
      v
Select Payment Method
      |
      v
Complete Sale
      |
      v
Persist Transaction
      |
      v
Update Inventory
      |
      v
Generate Receipt
```

Sales are associated with the user who recorded them, and access to sales records is scoped according to permissions.

**Warehouse selection note:** The multi-warehouse transaction flow must use a clearly identified source warehouse. The current implementation documentation identifies first-active-warehouse resolution as an outstanding operational decision; verify the desired behavior before relying on it for multi-warehouse production operations.

### 6.4 Purchasing and Supplier Management

The purchasing module supports purchase order management and receiving goods from suppliers.

A purchase order records the items ordered, quantities, supplier, warehouse, and related purchasing information.

The receiving process updates inventory and the relevant supplier and financial records.

```text
Supplier
    |
    v
Purchase Order
    |
    v
Purchase Items
    |
    v
Receive Goods
    |
    +-----------> Update Warehouse Stock
    |
    +-----------> Update Average Cost
    |
    +-----------> Record Transaction
```

Goods that have not yet been received are not treated as available inventory.

### 6.5 Returns and Refunds

Returns are stored as business documents with approval states.

Raising a return records the request but does not immediately reverse the original transaction.

Approval posts the required stock movement and financial reversal together.

This helps prevent duplicate returns and ensures that returned quantities are validated against the original transaction.

### 6.6 Payments and Expenses

Payments are linked to the relevant business documents.

Sales payments and purchase payments update their associated balances. Refunds are recorded as separate outgoing financial events.

Payments cannot simply be created as arbitrary amounts without a corresponding business document.

Expenses also include approval and transaction information. Only approved expenses are included in the financial calculation rules.

---

## 7. Financial Calculation Engine

One of StockFlow's important engineering decisions is that financial figures are calculated centrally in the backend.

The frontend does not independently calculate business-wide profit, cash flow, or account balances from incomplete paginated lists.

The calculation logic is centralized in:

```text
backend/src/main/java/com/inventory/backend/finance/
    FinancialCalculations.java
    FinancialCalculationService.java
    FinancialSummary.java
```

### Costing method: Moving Weighted Average

StockFlow uses moving weighted average costing.

The average cost is recalculated when new goods are received.

```text
New Average Cost =
(Value of Existing Stock + Cost of Received Stock)
---------------------------------------------------
(Existing Quantity + Received Quantity)
```

The cost is also frozen onto each sale line when the sale occurs.

This is important because changing a product's purchase price later should not silently rewrite the historical cost of an earlier sale.

### Financial metrics

| Metric              | Calculation approach                                           |
| ------------------- | -------------------------------------------------------------- |
| Net Sales           | Gross sales minus approved sales returns.                      |
| COGS                | Cost of quantities sold using the recorded sale-line cost.     |
| Gross Profit        | Net sales minus COGS.                                          |
| Gross Margin        | Gross profit divided by net sales.                             |
| Operating Expenses  | Approved expenses in the reporting period.                     |
| Net Profit          | Gross profit minus operating expenses.                         |
| Net Margin          | Net profit divided by net sales.                               |
| Net Cash Flow       | Completed incoming payments minus completed outgoing payments. |
| Accounts Receivable | Outstanding balances from sales.                               |
| Accounts Payable    | Outstanding balances for received purchase orders.             |
| Inventory Value     | Warehouse quantities multiplied by their average cost.         |

### Financial integrity principles

StockFlow deliberately distinguishes between:

- Revenue and collected cash.
- Purchase orders and goods actually received.
- Approved expenses and pending expenses.
- Sales and warehouse transfers.
- Sales tax and business revenue.
- Pending returns and approved returns.

These distinctions help prevent incorrect financial reporting.

The complete calculation rules are documented in [docs/financial-calculations.md](docs/financial-calculations.md).

---

## 8. Business Intelligence and AI

StockFlow includes a read-only business intelligence layer implemented inside the Java backend.

It analyzes historical business records to help users understand business performance and make better operational decisions.

**Important:** The forecasting and anomaly-detection engine is implemented using Java-based statistical methods. It does not depend on an external AI provider to perform its core calculations.

### 8.1 Sales Forecasting

The forecasting module analyzes historical sales and demand patterns to estimate future performance.

The model-selection process evaluates several approaches:

- Naive forecasting.
- Moving average.
- Holt level and trend.
- Holt forecasting with weekly seasonality.

Models are evaluated using chronological backtesting rather than random data splitting.

Forecast results can include:

- Predicted sales or demand.
- Prediction intervals.
- Mean Absolute Error (MAE).
- Root Mean Squared Error (RMSE).
- Mean Absolute Percentage Error (MAPE), where applicable.
- Model evaluation information.

The system also recognizes when there is insufficient historical information to produce a meaningful forecast.

### 8.2 Anomaly Detection

The anomaly detection module identifies unusual business activity that may require investigation.

Examples include:

- Unusual changes in revenue.
- Unexpected expense movements.
- Large stock adjustments.
- Repeated stock loss patterns.
- Unusual inventory activity.

The system uses robust statistical techniques, including median absolute deviation and modified z-scores.

Alerts are investigation prompts, not automatic conclusions of fraud or misconduct.

### 8.3 Inventory Optimization

The inventory optimization module analyzes product demand and available stock to calculate useful replenishment information.

It can provide:

- Average daily demand.
- Safety stock.
- Reorder points.
- Suggested replenishment quantities.
- Estimated days of stock remaining.
- Stock-out projections.

Supplier lead time currently uses a configurable default assumption rather than measured historical supplier performance. Recommendations communicate this limitation instead of presenting the assumption as a confirmed fact.

### 8.4 Financial Insights

The financial insight service explains business performance using figures from the centralized financial calculation service.

For example, it can explain changes in sales, profitability, expenses, and cash flow.

The insight layer does not create a second independent financial calculation engine. It uses the same financial summary that powers the reports.

### 8.5 Business Assistant

The business assistant allows authorized users to ask questions about recorded business information.

Examples:

- What were our net sales this month?
- Which products need replenishment?
- How did our expenses change?
- What is our current inventory value?
- How does this month compare with the previous month?

The assistant identifies the question, checks the relevant permissions, and retrieves the answer through trusted backend services.

Responses include relevant periods, assumptions, structured data, and report links where available.

### 8.6 Read-Only AI Design

The intelligence package is designed to read and analyze business information.

It does not have methods for approving payments, issuing refunds, changing stock, or posting accounting entries.

This separation is intentional: the intelligence layer provides recommendations and explanations, while authorized human users remain responsible for business decisions.

More information: [docs/intelligence.md](docs/intelligence.md).

---

## 9. Business Chatbot

StockFlow includes a chatbot for staff and customer interactions.

The chatbot uses structured backend services to answer supported questions instead of allowing generated text to independently invent business figures or execute business operations.

### Staff chatbot

Staff members authenticate through the normal JWT-based authentication system.

The chatbot checks both the permission to use the assistant and the permissions required by the specific information requested.

For example, having permission to use the assistant does not automatically grant access to profit and loss information.

### Customer chatbot

Customers use a separate session-based authentication flow.

Customer conversations and order-related queries are scoped to the authenticated customer session.

An order number alone does not grant access to another customer's information.

### Optional language-model integration

An optional OpenAI-compatible provider can improve how validated chatbot answers are phrased.

The distinction is important:

1. The backend obtains the actual figures through trusted business services.
2. The optional language model may rephrase the response.
3. A number guard checks generated numbers against the trusted results.
4. If the provider fails or produces unsupported figures, the system falls back to a deterministic response.

The default chatbot configuration is deterministic and does not require an external API key.

Provider credentials remain server-side and must never be exposed through frontend code or API responses.

Setting `CHATBOT_LLM_PHRASED=false` guarantees that the application makes no outbound language-model requests.

See [docs/chatbot.md](docs/chatbot.md) for configuration, security, and implementation details.

---

## 10. Reporting and Data Export

StockFlow provides fourteen documented reports covering business operations, inventory, sales, purchasing, and financial performance.

Reports support date filtering and server-side calculation.

The report export service supports:

- CSV exports.
- PDF exports.
- Consistent report data between the UI and downloaded files.
- Export row limits.
- CSV formula-injection neutralization.

Financial and operational report access is controlled by permissions.

The reports are generated from persisted business records, rather than independently reconstructed from incomplete frontend data.

---

## 11. Database Design

StockFlow uses PostgreSQL as its primary relational database.

Its business model includes the following major entities:

| Entity            | Responsibility                                                         |
| ----------------- | ---------------------------------------------------------------------- |
| User              | Stores system users and employee information.                          |
| Role              | Defines user roles.                                                    |
| Permission        | Defines authorized system actions.                                     |
| Product           | Stores product information.                                            |
| Category          | Organizes products.                                                    |
| Warehouse         | Represents a stock storage location.                                   |
| WarehouseStock    | Stores authoritative stock quantities and average costs per warehouse. |
| StockMovement     | Records inventory changes.                                             |
| Supplier          | Stores supplier information.                                           |
| Customer          | Stores customer information.                                           |
| PurchaseOrder     | Records purchasing transactions.                                       |
| PurchaseOrderItem | Records purchased products and quantities.                             |
| SaleOrder         | Records sales transactions.                                            |
| SaleOrderItem     | Records products and quantities sold.                                  |
| Payment           | Records incoming and outgoing financial transactions.                  |
| Expense           | Records business expenses.                                             |
| ReturnOrder       | Records return requests and approvals.                                 |
| AiModelVersion    | Stores intelligence model evaluation information.                      |
| AiQueryLog        | Records assistant query metadata and outcomes.                         |

### Important data integrity decisions

- Warehouse stock is the authoritative per-location balance.
- Stock movements are written through a centralized ledger service.
- Sale-line costs are preserved for historical financial accuracy.
- Payments are associated with business documents.
- Return approvals connect inventory and financial changes.
- Sensitive actions are recorded through audit mechanisms.

---

## 12. Project Structure

The repository separates the backend, frontend, and supporting infrastructure.

```text
inventory-system/
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/inventory/backend/
│   │   │   │   ├── config/
│   │   │   │   ├── controller/
│   │   │   │   ├── dto/
│   │   │   │   ├── entity/
│   │   │   │   ├── repository/
│   │   │   │   ├── service/
│   │   │   │   ├── security/
│   │   │   │   ├── finance/
│   │   │   │   └── ai/
│   │   │   └── resources/
│   │   └── test/
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend/
│   ├── public/
│   ├── src/
│   │   ├── app/
│   │   ├── components/
│   │   ├── contexts/
│   │   ├── hooks/
│   │   ├── lib/
│   │   ├── providers/
│   │   ├── store/
│   │   └── __tests__/
│   ├── package.json
│   ├── package-lock.json
│   └── Dockerfile
│
├── docs/
│   ├── architecture.md
│   ├── financial-calculations.md
│   ├── intelligence.md
│   ├── chatbot.md
│   ├── roles-and-permissions.md
│   ├── deployment.md
│   └── ...
│
├── .github/
│   └── workflows/
│       ├── ci.yml
│       └── cd.yml
│
├── docker-compose.yml
├── render.yaml
├── .env-example
├── .gitignore
└── README.md
```

The exact contents may evolve as the application develops.

---

## 13. REST API

The backend exposes RESTful endpoints under the following base path:

```text
/api/v1
```

Local development API:

```text
http://localhost:8081/api/v1
```

### Main API groups

| Module         | Example endpoint       | Purpose                                        |
| -------------- | ---------------------- | ---------------------------------------------- |
| Authentication | `POST /auth/login`     | User authentication.                           |
| Registration   | `POST /auth/register`  | User registration with supported default role. |
| Products       | `/products`            | Product management.                            |
| Categories     | `/categories`          | Product category management.                   |
| Suppliers      | `/suppliers`           | Supplier management.                           |
| Customers      | `/customers`           | Customer management.                           |
| Sales          | `/sales`               | Sales transactions and records.                |
| Payments       | `/payments`            | Document-associated payments and refunds.      |
| Dashboard      | `/dashboard/stats`     | Operational dashboard statistics.              |
| Finance        | `/dashboard/financial` | Permission-protected financial summary.        |
| Insights       | `/dashboard/insights`  | Business intelligence information.             |
| Reports        | `/reports`             | Business reporting and exports.                |
| Chatbot        | `/chatbot`             | Staff and customer assistant endpoints.        |

### Example login request

```http
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "identifier": "username-or-email",
  "password": "your-password"
}
```

Authenticated requests use the following header:

```http
Authorization: Bearer YOUR_JWT_TOKEN
```

The API enforces authorization on the backend. The endpoint examples above are representative; consult the controllers and API documentation for the complete request and response contracts.

---

## 14. Local Development Setup

### Prerequisites

Install the following tools:

| Tool           | Requirement                       |
| -------------- | --------------------------------- |
| Git            | Latest stable version             |
| Java           | 17                                |
| Maven          | 3.9+                              |
| Node.js        | 20.9+                             |
| npm            | Compatible with installed Node.js |
| PostgreSQL     | Compatible PostgreSQL release     |
| Docker Desktop | Recommended                       |
| Docker Compose | Recommended                       |

Verify the installations:

```bash
java -version
mvn -version
node -version
npm -version
git --version
docker --version
docker compose version
```

### Step 1: Clone the repository

```bash
git clone https://github.com/yemom/inventory-system.git

cd inventory-system
```

### Step 2: Configure environment variables

Start with the example environment file:

```bash
cp .env-example .env
```

On Windows PowerShell:

```powershell
Copy-Item .env-example .env
```

Edit `.env` and provide the required local configuration.

Generate a strong JWT secret:

```bash
openssl rand -hex 32
```

Provide your own secure database and administrator credentials. The backend intentionally refuses to start with an unsafe default JWT secret or missing required administrator credentials.

**Never commit `.env` or real credentials to GitHub.**

### Step 3: Start the application with Docker Compose

From the repository root:

```bash
docker compose up --build
```

To run in the background:

```bash
docker compose up -d --build
```

The documented local service addresses are:

| Service    | Address                      |
| ---------- | ---------------------------- |
| Frontend   | http://localhost:3005        |
| Backend    | http://localhost:8081        |
| REST API   | http://localhost:8081/api/v1 |
| PostgreSQL | localhost:5434               |

Check running containers:

```bash
docker compose ps
```

View logs:

```bash
docker compose logs -f
```

Stop the application:

```bash
docker compose down
```

To stop containers and remove database volumes:

```bash
docker compose down -v
```

**Warning:** The `-v` option removes persistent Docker volumes and can permanently delete local database data. Use it only when you intend to reset the environment.

### Step 4: Run the backend independently

If you want to run the backend outside Docker, ensure PostgreSQL is running and configure the backend environment.

```bash
cd backend

mvn clean install

mvn spring-boot:run
```

Backend:

```text
http://localhost:8081
```

Health check:

```text
http://localhost:8081/actuator/health
```

### Step 5: Run the frontend independently

Open another terminal:

```bash
cd frontend

npm install

npm run dev
```

Frontend:

```text
http://localhost:3005
```

The frontend API URL is configured through:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api/v1
NEXT_PUBLIC_APP_NAME=StockFlow
NEXT_PUBLIC_APP_URL=http://localhost:3005
```

Create or update `frontend/.env.local` with the appropriate values.

Remember that `NEXT_PUBLIC_*` variables are included in the browser bundle at build time. They are public configuration values, not secrets. Changing them requires restarting the development server or rebuilding the frontend.

---

## 15. Testing and Quality Assurance

Testing is an important part of StockFlow because inventory and financial operations must produce consistent results.

### Backend testing

The backend uses:

- JUnit.
- Mockito.
- Spring Boot Test.
- Spring Security Test.
- Testcontainers.

Run backend tests:

```bash
cd backend

mvn test
```

Run the complete verification lifecycle:

```bash
mvn clean verify
```

Integration tests using Testcontainers require a working Docker daemon.

### Frontend testing

The frontend uses:

- Jest.
- React Testing Library.
- jest-dom.
- jsdom.

Run frontend tests:

```bash
cd frontend

npm test -- --ci
```

Run TypeScript checks:

```bash
npm run type-check
```

Run linting:

```bash
npm run lint
```

Build the production frontend:

```bash
npm run build
```

### Repository-documented test results

The repository README documents the following results from its reported test run:

| Test / Check              | Documented result                            |
| ------------------------- | -------------------------------------------- |
| Backend tests             | 452 tests, 0 failures, 0 errors              |
| Frontend tests            | 225 tests across 22 suites, all passing      |
| TypeScript                | Clean type check                             |
| ESLint                    | 0 errors, 55 warnings                        |
| Frontend production build | Compiled successfully and routes prerendered |

These are repository-documented results, not a new test run performed while preparing this README. Re-run the commands above against the current checkout to verify the latest state.

**Testing note:** The repository recommends running backend and frontend test suites separately because running both worker-heavy suites simultaneously can exhaust available memory. Backend integration tests also require Docker for Testcontainers.

---

## 16. Deployment

StockFlow includes configuration for a separate frontend and backend deployment.

| Component             | Deployment configuration |
| --------------------- | ------------------------ |
| Backend               | Render                   |
| Frontend              | Vercel                   |
| Database              | Managed PostgreSQL       |
| Cache / rate limiting | Redis                    |
| Containerization      | Docker                   |

### Backend configuration

The backend requires secure environment variables, including:

```env
DATABASE_URL=your_database_connection
DATABASE_USERNAME=your_database_username
DATABASE_PASSWORD=your_database_password

JWT_SECRET=your_generated_secret

SUPER_ADMIN_USERNAME=your_admin_username
SUPER_ADMIN_PASSWORD=your_secure_admin_password

CORS_ALLOWED_ORIGINS=https://your-frontend-domain
```

Optional chatbot configuration:

```env
CHATBOT_PROVIDER=deterministic
CHATBOT_LLM_PHRASED=false
```

The deterministic chatbot does not require an external API key.

### Frontend configuration

Configure the following in the Vercel project settings:

```env
NEXT_PUBLIC_API_BASE_URL=https://your-backend-domain/api/v1
NEXT_PUBLIC_APP_URL=https://your-frontend-domain
```

The frontend API URL is compiled into the browser bundle during the build. Redeploy after changing it.

### Health checks

The backend exposes:

```text
GET /actuator/health
GET /api/v1/dashboard/health
```

### Deployment verification

A deployment should not be considered successful merely because a hosting service reports a successful build.

Verify:

- Backend health.
- Database connectivity.
- Frontend-to-backend communication.
- CORS configuration.
- Authentication.
- Permission enforcement.
- Core business transactions.
- Reports and exports.
- Backup and recovery procedures.

The repository contains deployment configuration and documentation, but a configured deployment URL should not be interpreted as proof that every production workflow has been independently verified.

See [docs/deployment.md](docs/deployment.md) for the detailed deployment guide.

---

## 17. Known Limitations and Future Improvements

StockFlow has a substantial set of implemented business workflows, but several areas require additional development or operational decisions.

| Area                    | Current limitation                                                                           | Possible improvement                                                         |
| ----------------------- | -------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------- |
| Batch and lot tracking  | No complete lot-level tracking.                                                              | Add batch identification and expiry-aware stock allocation.                  |
| Supplier lead time      | Replenishment uses a configurable default.                                                   | Learn lead times from historical purchasing records.                         |
| Seasonality             | Forecasting focuses on weekly seasonality.                                                   | Add annual and longer-term seasonal analysis when sufficient history exists. |
| Multi-currency          | Currency is stored for transaction provenance, but conversion arithmetic is not implemented. | Add exchange-rate management and multi-currency accounting rules.            |
| Forecast execution      | Forecasts are calculated per request.                                                        | Introduce scheduled forecasting and cached model results as scale requires.  |
| Database migrations     | Hibernate schema update is currently used.                                                   | Adopt Flyway or Liquibase for controlled production schema changes.          |
| Concurrent updates      | Some non-stock entities do not have optimistic version fields.                               | Add version-based concurrency control where required.                        |
| Dashboard customization | Dashboard is a defined operational view.                                                     | Consider customizable widgets and role-specific workspaces.                  |
| Warehouse selection     | Multi-warehouse transaction behavior needs a confirmed business decision.                    | Make the selected warehouse explicit where operationally required.           |

These improvements should be prioritized according to actual business requirements, data quality, and deployment scale.

---

## 18. Security Considerations

StockFlow handles business transactions and potentially sensitive financial information. Security must therefore be applied throughout the application.

Current security mechanisms include:

- JWT-based stateless authentication.
- BCrypt password hashing.
- Role-based and permission-based authorization.
- Backend endpoint protection.
- Service-level authorization checks.
- Request validation.
- CORS configuration.
- Rate limiting.
- Audit records for important operations.
- Server-side handling of chatbot credentials.
- Customer-specific chatbot session isolation.

### Important security practices

1. Never commit `.env` files or production secrets.
2. Use strong JWT signing keys.
3. Rotate credentials that may have been exposed.
4. Restrict database access.
5. Use HTTPS in production.
6. Configure CORS with exact trusted origins.
7. Back up the database and test restoration.
8. Review role permissions before creating staff accounts.
9. Do not expose server-side credentials through frontend variables.
10. Use controlled database migrations before production schema changes.

---

## 19. Development Guidelines

To keep the codebase maintainable, new features should follow the existing architecture.

### Backend development flow

```text
Business Requirement
       |
       v
Entity / Data Model
       |
       v
DTO and Validation
       |
       v
Repository
       |
       v
Service and Business Logic
       |
       v
Permission Enforcement
       |
       v
Controller / REST API
       |
       v
Unit and Integration Tests
```

### Frontend development flow

```text
User Interface
       |
       v
Reusable Components
       |
       v
Hooks / React Query
       |
       v
Centralized API Client
       |
       v
Backend REST API
```

### Git commit convention

Use descriptive commit messages:

```text
feat: add warehouse management
fix: correct inventory stock calculation
refactor: improve financial calculation service
test: add sales integration tests
docs: update project documentation
```

Create a feature branch before implementing a change:

```bash
git checkout -b feature/new-feature

git add .

git commit -m "feat: implement new feature"

git push origin feature/new-feature
```

---

## 20. Contribution

Contributions, suggestions, and constructive feedback are welcome.

To contribute:

1. Fork the repository.
2. Create a feature branch.
3. Implement your changes.
4. Add or update tests.
5. Run backend and frontend verification.
6. Commit your changes with a meaningful message.
7. Push your branch.
8. Open a pull request describing the change.

Please include the business problem, implementation details, and testing performed when submitting a contribution.

---

## 21. Project Documentation

For deeper technical information, refer to the documentation directory.

| Document                                                         | Description                                                                     |
| ---------------------------------------------------------------- | ------------------------------------------------------------------------------- |
| [docs/architecture.md](docs/architecture.md)                     | Application architecture, data model, and design decisions.                     |
| [docs/financial-calculations.md](docs/financial-calculations.md) | Financial formulas, costing method, and accounting rules.                       |
| [docs/intelligence.md](docs/intelligence.md)                     | Forecasting, anomaly detection, inventory optimization, and business assistant. |
| [docs/chatbot.md](docs/chatbot.md)                               | Chatbot architecture, configuration, security, and fallback behavior.           |
| [docs/roles-and-permissions.md](docs/roles-and-permissions.md)   | Role and permission matrix with approval workflows.                             |
| [docs/deployment.md](docs/deployment.md)                         | Environment configuration and deployment instructions.                          |

---

## 22. About the Project

StockFlow was developed as a software engineering project to address practical challenges in inventory management and business operations.

The project focuses on more than building CRUD screens. It emphasizes the engineering decisions behind reliable business software: transaction consistency, historical cost accuracy, secure authorization, auditability, financial integrity, and useful data-driven insights.

It brings together backend engineering, frontend development, database design, security, testing, and statistical analysis in one application.

The project is also an opportunity to explore how software engineering can help businesses make better operational decisions using accurate and accessible information.

---

## 23. Author

**Esrom Basazinaw**  
Software Engineering Student  
Addis Ababa Institute of Technology (AAiT)  
Addis Ababa, Ethiopia

- GitHub: [@yemom](https://github.com/yemom)
- Portfolio: [esrom12-portfolio.vercel.app](https://esrom12-portfolio.vercel.app/)
- LinkedIn: [Esrom Basazinaw](https://www.linkedin.com/in/esrom-basazinew-65102a339/)

---

## 24. Repository

[https://github.com/yemom/inventory-system](https://github.com/yemom/inventory-system)

---

## License

No open-source license is currently specified for this repository. If the project is intended for public reuse and distribution, add an appropriate license and make its terms clear.

---

<p align="center">
  <strong>StockFlow — Turning Business Operations into Clear, Connected Information.</strong>
</p>
