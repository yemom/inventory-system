# StockFlow

## Inventory Management and Business Transaction System

StockFlow is a production-oriented inventory management and business transaction system designed for medium-sized stores and businesses.

The system provides centralized management of products, categories, suppliers, customers, users, inventory information, authentication, authorization, and point-of-sale workflows.

It is built with a modern full-stack architecture using Spring Boot for the backend, Next.js for the frontend, PostgreSQL for data persistence, JWT-based authentication, and Docker for containerized deployment.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Core Features](#core-features)
- [Technology Stack](#technology-stack)
- [System Architecture](#system-architecture)
- [Authentication and Authorization](#authentication-and-authorization)
- [User and Role Management](#user-and-role-management)
- [Product Management](#product-management)
- [Category Management](#category-management)
- [Supplier Management](#supplier-management)
- [Customer Management](#customer-management)
- [Inventory Management](#inventory-management)
- [Point of Sale](#point-of-sale)
- [Dashboard and Reporting](#dashboard-and-reporting)
- [Business Intelligence](#business-intelligence)
- [REST API](#rest-api)
- [Project Structure](#project-structure)
- [Backend Structure](#backend-structure)
- [Frontend Structure](#frontend-structure)
- [Database Model](#database-model)
- [API Authentication Flow](#api-authentication-flow)
- [Local Development Setup](#local-development-setup)
- [Running the Backend](#running-the-backend)
- [Running the Frontend](#running-the-frontend)
- [Running with Docker](#running-with-docker)
- [Environment Configuration](#environment-configuration)
- [Reports and Exports](#reports-and-exports)
- [Testing](#testing)
- [Project Status](#project-status)
- [Files Changed](#files-changed)
- [Build and Production](#build-and-production)
- [Security](#security)
- [Development Guidelines](#development-guidelines)
- [Known Implementation Notes](#known-implementation-notes)
- [Future Improvements](#future-improvements)
- [Project Status](#project-status)
- [Author](#author)
- [License](#license)

---

# Project Overview

StockFlow is designed to provide businesses with a centralized platform for managing their daily inventory and business operations.

The system is divided into two main applications:

1. Backend API
2. Frontend Web Application

The backend provides RESTful APIs, authentication, authorization, business logic, database access, and security.

The frontend provides the user interface for interacting with the inventory system.

PostgreSQL is used as the primary relational database.

Docker Compose is provided for running the main services together.

---

# Core Features

## Authentication

- User registration
- User login
- JWT authentication
- Secure password hashing using BCrypt
- Stateless authentication
- Authentication token stored on the frontend
- Automatic authorization header injection
- Automatic handling of expired/invalid authentication sessions

## Authorization

- Role-based access control
- Permission-based authorization
- Protected backend endpoints
- Spring Security integration
- Method-level security
- JWT-based request authentication

## User Management

The system supports user information including:

- Username
- First name
- Last name
- Email
- Phone
- Gender
- Date of birth
- Address
- Employee ID
- Job title
- Department
- Branch
- Warehouse
- Date joined
- Account status
- Profile photo
- Login information

## Product Management

The product management module supports:

- Product creation
- Product updates
- Product retrieval
- Product search
- SKU management
- Barcode management
- Product categories
- Units
- Purchase prices
- Selling prices
- Minimum selling prices
- Stock quantities
- Minimum stock levels
- Maximum stock levels
- Reorder levels
- Batch tracking
- Expiry tracking
- Product activation/deactivation

## Category Management

Categories support:

- Category creation
- Category updates
- Category deletion
- Category listing
- Root categories
- Parent-child category relationships
- Subcategories

## Supplier Management

Supplier records include:

- Supplier number
- Company name
- Contact person
- Phone
- Email
- Address
- Tax number
- Payment terms
- Outstanding balance
- Supplier status
- Notes

## Customer Management

Customer records include:

- Customer number
- Customer name
- Phone
- Email
- Address
- Customer type
- Credit limit
- Outstanding balance
- Customer status
- Notes

## Inventory

The inventory module provides information about:

- Current stock quantity
- Low-stock products
- Out-of-stock products
- Inventory value
- Reorder levels
- Product stock limits
- Product activity status

The backend also provides protected database operations for inventory-sensitive product updates.

## Point of Sale

The frontend provides a POS interface with support for:

- Product search
- Category filtering
- Shopping cart
- Customer selection
- Quantity management
- Discounts
- VAT calculation
- Cash payments
- Bank payments
- Mobile payments
- Tendered amount
- Change calculation
- Receipt generation
- Receipt printing

The current POS implementation prepares the checkout and receipt information on the frontend. Persistent sales transaction storage through a dedicated backend sales endpoint is not yet implemented.

---

# Technology Stack

## Backend

| Technology | Purpose |
|---|---|
| Java 17 | Backend programming language |
| Spring Boot 3.2.3 | Backend framework |
| Spring Web | REST API |
| Spring Data JPA | Database access |
| Hibernate | ORM |
| Spring Security | Authentication and authorization |
| JWT | Stateless authentication |
| BCrypt | Password hashing |
| PostgreSQL | Relational database |
| Lombok | Boilerplate reduction |
| Maven | Dependency management and build |
| JUnit | Unit testing |
| Mockito | Mock-based testing |
| Testcontainers | Integration testing |

## Frontend

| Technology | Purpose |
|---|---|
| Next.js | React web framework |
| React | User interface |
| TypeScript | Type-safe development |
| Axios | HTTP client |
| TanStack React Query | Server state management |
| Zustand | Client state management |
| React Hook Form | Form management |
| Zod | Validation |
| Tailwind CSS | Styling |
| Radix UI | UI primitives |
| Lucide React | Icons |
| Recharts | Data visualization |
| Jest | Testing |
| React Testing Library | Component testing |

## Infrastructure

| Technology | Purpose |
|---|---|
| Docker | Containerization |
| Docker Compose | Multi-container development |
| PostgreSQL | Database service |
| Node.js | Frontend runtime/build |
| Maven | Backend build |

---

# System Architecture

The system follows a layered full-stack architecture.

```text
                         STOCKFLOW
                             |
             +---------------+---------------+
             |                               |
             v                               v
       Next.js Frontend                 Spring Boot API
             |                               |
             |                               |
             v                               v
      Axios API Client                Spring Security
             |                               |
             |                               v
             |                         JWT Validation
             |                               |
             |                               v
             |                         Controllers
             |                               |
             |                               v
             |                           Services
             |                               |
             |                               v
             |                         Repositories
             |                               |
             +-------------------------------+
                             |
                             v
                       PostgreSQL
```

---

# Backend Architecture

The backend follows a layered architecture:

```text
Controller
    |
    v
Service
    |
    v
Repository
    |
    v
Entity
    |
    v
PostgreSQL
```

Security is applied before requests reach protected controllers.

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
Authorization
     |
     v
Controller
     |
     v
Service
     |
     v
Repository
     |
     v
Database
```

This structure separates:

- HTTP handling
- Business logic
- Database access
- Security
- Persistence models

---

# Authentication and Authorization

StockFlow uses JWT-based stateless authentication.

After a successful login, the backend returns a JWT token.

The frontend stores the token and automatically sends it with subsequent API requests.

The request header follows the format:

```http
Authorization: Bearer <JWT_TOKEN>
```

The backend JWT authentication filter:

1. Reads the `Authorization` header.
2. Checks whether it contains a Bearer token.
3. Extracts the JWT.
4. Validates the token.
5. Extracts authentication information.
6. Loads the corresponding user details.
7. Creates a Spring Security authentication object.
8. Stores the authentication in the SecurityContext.
9. Allows the request to continue.

Invalid or missing authentication is rejected for protected endpoints.

---

# User and Role Management

The system contains a user and permission model.

Users have an assigned role.

Roles can have multiple permissions.

Conceptually:

```text
User
 |
 +---- Role
        |
        +---- Permission
        |
        +---- Permission
        |
        +---- Permission
```

The backend currently includes role and permission support for controlling access to protected functionality.

The authentication response includes information such as:

- User ID
- Username
- Email
- Name
- Role
- Permissions
- JWT token

---

# Product Management

Products are one of the core entities in StockFlow.

A product contains information such as:

```text
Product
Γö£ΓöÇΓöÇ ID
Γö£ΓöÇΓöÇ SKU
Γö£ΓöÇΓöÇ Barcode
Γö£ΓöÇΓöÇ Name
Γö£ΓöÇΓöÇ Unit
Γö£ΓöÇΓöÇ Description
Γö£ΓöÇΓöÇ Category
Γö£ΓöÇΓöÇ Purchase Price
Γö£ΓöÇΓöÇ Selling Price
Γö£ΓöÇΓöÇ Minimum Selling Price
Γö£ΓöÇΓöÇ Minimum Stock
Γö£ΓöÇΓöÇ Maximum Stock
Γö£ΓöÇΓöÇ Reorder Level
Γö£ΓöÇΓöÇ Quantity
Γö£ΓöÇΓöÇ Batch Tracking
Γö£ΓöÇΓöÇ Expiry Tracking
Γö£ΓöÇΓöÇ Active Status
Γö£ΓöÇΓöÇ Created At
ΓööΓöÇΓöÇ Updated At
```

SKU values are unique.

Products can be searched using:

- Name
- SKU
- Barcode

Products can also be filtered by category.

Products are deactivated instead of physically deleted through the product delete operation.

---

# Category Management

Categories organize products into meaningful groups.

The category model supports hierarchical relationships.

Example:

```text
Electronics
|
+-- Computers
|   |
|   +-- Laptops
|   +-- Desktop Computers
|
+-- Accessories
    |
    +-- Keyboards
    +-- Mouse
```

The API supports retrieving:

- All categories
- Root categories
- Individual categories

---

# Supplier Management

Suppliers represent businesses or organizations that provide products.

Supplier information includes business and financial information.

Example:

```text
Supplier
Γö£ΓöÇΓöÇ Supplier Number
Γö£ΓöÇΓöÇ Company Name
Γö£ΓöÇΓöÇ Contact Person
Γö£ΓöÇΓöÇ Phone
Γö£ΓöÇΓöÇ Email
Γö£ΓöÇΓöÇ Address
Γö£ΓöÇΓöÇ Tax Number
Γö£ΓöÇΓöÇ Payment Terms
Γö£ΓöÇΓöÇ Outstanding Balance
Γö£ΓöÇΓöÇ Status
ΓööΓöÇΓöÇ Notes
```

The system supports creating, retrieving, searching, and updating suppliers.

---

# Customer Management

Customers represent people or organizations purchasing products.

Customer information includes:

```text
Customer
Γö£ΓöÇΓöÇ Customer Number
Γö£ΓöÇΓöÇ Name
Γö£ΓöÇΓöÇ Phone
Γö£ΓöÇΓöÇ Email
Γö£ΓöÇΓöÇ Address
Γö£ΓöÇΓöÇ Customer Type
Γö£ΓöÇΓöÇ Credit Limit
Γö£ΓöÇΓöÇ Outstanding Balance
Γö£ΓöÇΓöÇ Status
ΓööΓöÇΓöÇ Notes
```

The backend supports customer creation, retrieval, searching, and updates.

---

# Inventory Management

Inventory information is centered around product stock levels.

The system can identify:

```text
Current Stock
     |
     +-- Normal Stock
     |
     +-- Low Stock
     |
     +-- Out of Stock
```

The backend provides dashboard-level inventory calculations including:

- Active product count
- Low-stock product count
- Out-of-stock product count
- Total inventory value

Low-stock calculations use the product's reorder level.

---

# Point of Sale

The POS interface is available through the frontend sales module.

The checkout workflow includes:

```text
Search Product
      |
      v
Select Product
      |
      v
Add to Cart
      |
      v
Update Quantity
      |
      v
Select Customer
      |
      v
Apply Discount
      |
      v
Calculate VAT
      |
      v
Select Payment Method
      |
      v
Enter Tendered Amount
      |
      v
Calculate Change
      |
      v
Generate Receipt
```

Supported payment methods include:

- Cash
- Bank
- Mobile

The POS interface also supports receipt printing.

### Current POS Limitation

The current frontend checkout implementation prepares the order and receipt data locally.

It does not currently persist the completed sale through a dedicated backend sales transaction endpoint.

Therefore, the POS should currently be considered a frontend checkout workflow rather than a fully persisted sales transaction system.

---

# Dashboard and Reporting

The dashboard is served in three parts, each gated on the permission the figures in
it actually require - not on a single "can view the dashboard" that every role
holds.

| Endpoint | Permission | Returns |
|---|---|---|
| `GET /api/v1/dashboard/stats` | `DASHBOARD_VIEW` | Operational counts. No money. |
| `GET /api/v1/dashboard/financial` | `FINANCIAL_REPORT_VIEW` | Net sales, gross and net profit, margins, cash, receivables, payables, inventory value. |
| `GET /api/v1/dashboard/insights` | `AI_INSIGHT_VIEW` | Explanations, alerts and recommended actions. |
| `GET /api/v1/dashboard/sales-summary` | `DASHBOARD_VIEW` | Orders, completed sales, gross, discounts, returns, net, collected, outstanding and average order value for a period. **Scoped on the server**: a caller holding `SALES_REPORT_VIEW` sees every seller (`scope: ALL_SALES`), anyone else sees only the orders they recorded (`scope: OWN_SALES`). There is no parameter that widens this. |
| `GET /api/v1/sales` | `SALE_READ` | Orders, paginated. Accepts `search` (order number or customer name), `status`, `paymentStatus` and standard Spring `Pageable` parameters. All filters are applied **in the database**, and the same seller scoping applies as above. |

### Multi-warehouse: which location a transaction belongs to

With **one** active warehouse the backend resolves it and no screen asks.
With **two or more** the caller must say, because stock leaves a specific place
and the movement is recorded against it. Guessing would put inventory somewhere
nobody chose.

| Situation | Result |
|---|---|
| One active warehouse, no `warehouseId` sent | Resolved automatically. Sale succeeds. |
| Two or more active, no `warehouseId` sent | **`409 WAREHOUSE_REQUIRED`** — "Choose the warehouse this transaction belongs to." The POS shows a picker and disables **Complete Sale** until one is chosen. |
| A `warehouseId` that does not exist | `400` — the warehouse is not found. |
| Named warehouse has insufficient stock | `400` — "Insufficient stock for 'X' in Y. Available: N, Requested: M." |

A `409` here is deliberate and is not an outage. It means *this request needs
different input*. It used to arrive as a `500`, which told the cashier the
application had crashed at the moment they were trying to take money.

**Creating a product with opening stock in a multi-warehouse business** needs a
warehouse too. The product form shows "Opening Stock Warehouse" when more than
one is active. Without it the opening balance is recorded with no location, and
the new product starts life holding stock that no till can draw on.

`warehouse_stock` holds the authoritative per-location balance;
`products.quantity` is republished from it on every movement. On first start, if
`warehouse_stock` is empty but a movement ledger exists, the balances are
**rebuilt from that ledger** (`WarehouseStockBackfillRunner`). This is a
derivation, not a guess — the rebuilt totals are reconciled against
`products.quantity` and logged as a mismatch if they disagree. It runs only when
the table is empty, so it never overwrites live balances.

**Every financial figure comes from the backend calculation service.** It used to
be assembled in the browser from lists of sales and purchase orders, filtered the
same way on each screen - which meant "gross profit" was really "revenue minus
everything ever purchased", and two screens could print different numbers for the
same month. The formulas, the costing method and the accounting policies are
documented in **[docs/financial-calculations.md](docs/financial-calculations.md)**.

Stock is valued at a **moving weighted average cost**, recalculated on every goods
receipt, and the cost is **frozen onto each sale line at the moment of sale** so a
later price change cannot rewrite historical profit.

---

# Business Intelligence

A read-only analysis layer in `com.inventory.backend.ai` that reads the same
tables, through the same repositories, under the same permissions, as the reports.

**There is no external AI provider.** No API key, no outbound request, no data
leaves the machine. The models are Holt exponential smoothing with weekly
seasonality, a robust z-score, and a service-level safety-stock formula -
implemented in Java, auditable, and evaluated by backtesting on held-out history.

**It cannot change anything.** There is no write method in the package, so it
cannot approve a payment, issue a refund, move stock or post an accounting entry.
There are deliberately no AI write permissions, because there is nothing to gate.

| Capability | What it produces |
|---|---|
| Forecasting | Sales and per-product demand, with prediction intervals and the model's measured error on held-out data |
| Anomaly detection | Investigation alerts on revenue, expenses and stock, each carrying the evidence that produced it |
| Inventory optimisation | Reorder points, safety stock and suggested quantities, with the arithmetic shown |
| Financial insights | Explanations that quote the calculation layer's figures and link to the report showing them |
| Business assistant | Questions answered from validated figures, with period, assumptions and source links |
| Business chatbot | Conversational questions from staff and customers, answered by the same services — with an optional model that only re-phrases validated results |

Every model version is recorded with its training period, sample size and measured
error; retraining supersedes rather than overwrites, so a figure already reported
stays traceable. Every assistant question is logged with its intent, resolved
parameters and outcome.

A day is raised as an alert only when it is **both** statistically unusual and
materially different in money - a small business has a small spread, and without
the second test its ordinary variation fills the alerts panel and trains people to
dismiss it.

Fully described in **[docs/intelligence.md](docs/intelligence.md)**; the
chatbot — its optional OpenAI-compatible phrasing layer, key setup and
guarantees — in **[docs/chatbot.md](docs/chatbot.md)**.

---

# REST API

The backend API uses the following base path:

```text
/api/v1
```

When running locally with the default backend port:

```text
http://localhost:8081/api/v1
```

When using Docker Compose, the frontend communicates with the backend through:

```text
http://localhost:8081/api/v1
```

---

# Authentication Endpoints

## Login

```http
POST /api/v1/auth/login
```

Example request:

```json
{
  "identifier": "username-or-email",
  "password": "your-password"
}
```

The response contains authentication information including a JWT token.

---

## Register

```http
POST /api/v1/auth/register
```

Registration creates a user with the supported default registration role.

---

# Product Endpoints

## Get Products

```http
GET /api/v1/products
```

Supports searching and pagination.

## Get Product

```http
GET /api/v1/products/{id}
```

## Create Product

```http
POST /api/v1/products
```

Requires appropriate product creation permissions.

## Update Product

```http
PUT /api/v1/products/{id}
```

Requires appropriate product update permissions.

## Delete / Deactivate Product

```http
DELETE /api/v1/products/{id}
```

The product is deactivated rather than physically removed.

---

# Category Endpoints

```http
GET    /api/v1/categories
GET    /api/v1/categories/roots
GET    /api/v1/categories/{id}
POST   /api/v1/categories
PUT    /api/v1/categories/{id}
DELETE /api/v1/categories/{id}
```

---

# Customer Endpoints

```http
GET  /api/v1/customers
GET  /api/v1/customers/{id}
POST /api/v1/customers
PUT  /api/v1/customers/{id}
```

Customer listing supports searching and pagination.

---

# Supplier Endpoints

```http
GET  /api/v1/suppliers
GET  /api/v1/suppliers/{id}
POST /api/v1/suppliers
PUT  /api/v1/suppliers/{id}
```

Supplier listing supports searching and pagination.

---

# API Permissions

Product operations are protected using permissions such as:

```text
PRODUCT_READ
PRODUCT_CREATE
PRODUCT_UPDATE
PRODUCT_DEACTIVATE
INVENTORY_READ
```

Additional permissions can be added through the role-permission system.

---

# Project Structure

The repository is organized into separate backend and frontend applications.

**Documentation**

| Document | Covers |
|---|---|
| docs/architecture.md | How the pieces fit, the data model, the security model, the deliberate limitations |
| docs/financial-calculations.md | Every formula, the costing method, the accounting policies, and what is deliberately *not* counted |
| docs/intelligence.md | How the forecasting, anomaly detection, inventory advice and assistant work, and how they are evaluated |
| docs/chatbot.md | The business chatbot: deterministic tools vs the optional LLM phrasing layer, key creation, local and deployed configuration, security guarantees |
| docs/roles-and-permissions.md | The role to permission matrix |
| docs/deployment.md | Deployment, configuration and environment variables |

```text
inventory-system/
Γöé
Γö£ΓöÇΓöÇ backend/
Γöé   Γö£ΓöÇΓöÇ src/
Γöé   Γöé   Γö£ΓöÇΓöÇ main/
Γöé   Γöé   Γöé   Γö£ΓöÇΓöÇ java/
Γöé   Γöé   Γöé   ΓööΓöÇΓöÇ resources/
Γöé   Γöé   Γöé
Γöé   Γöé   ΓööΓöÇΓöÇ test/
Γöé   Γöé
Γöé   Γö£ΓöÇΓöÇ pom.xml
Γöé   Γö£ΓöÇΓöÇ Dockerfile
Γöé   ΓööΓöÇΓöÇ ...
Γöé
Γö£ΓöÇΓöÇ frontend/
Γöé   Γö£ΓöÇΓöÇ public/
Γöé   Γö£ΓöÇΓöÇ src/
Γöé   Γöé   Γö£ΓöÇΓöÇ app/
Γöé   Γöé   Γö£ΓöÇΓöÇ components/
Γöé   Γöé   Γö£ΓöÇΓöÇ contexts/
Γöé   Γöé   Γö£ΓöÇΓöÇ lib/
Γöé   Γöé   Γö£ΓöÇΓöÇ providers/
Γöé   Γöé   Γö£ΓöÇΓöÇ store/
Γöé   Γöé   ΓööΓöÇΓöÇ tests/
Γöé   Γöé
Γöé   Γö£ΓöÇΓöÇ package.json
Γöé   Γö£ΓöÇΓöÇ package-lock.json
Γöé   Γö£ΓöÇΓöÇ Dockerfile
Γöé   Γö£ΓöÇΓöÇ next.config.*
Γöé   Γö£ΓöÇΓöÇ tsconfig.json
Γöé   ΓööΓöÇΓöÇ ...
Γöé
Γö£ΓöÇΓöÇ .local-pg/
Γö£ΓöÇΓöÇ docker-compose.yml
Γö£ΓöÇΓöÇ .gitignore
ΓööΓöÇΓöÇ README.md
```

---

# Backend Structure

The backend is organized under the main Java package:

```text
com.inventory.backend
```

The backend follows a modular Spring Boot architecture.

```text
backend/
ΓööΓöÇΓöÇ src/
    Γö£ΓöÇΓöÇ main/
    Γöé   Γö£ΓöÇΓöÇ java/
    Γöé   Γöé   ΓööΓöÇΓöÇ com/
    Γöé   Γöé       ΓööΓöÇΓöÇ inventory/
    Γöé   Γöé           ΓööΓöÇΓöÇ backend/
    Γöé   Γöé               Γöé
    Γöé   Γöé               Γö£ΓöÇΓöÇ BackendApplication.java
    Γöé   Γöé               Γö£ΓöÇΓöÇ config/
    Γöé   Γöé               Γö£ΓöÇΓöÇ controller/
    Γöé   Γöé               Γö£ΓöÇΓöÇ dto/
    Γöé   Γöé               Γö£ΓöÇΓöÇ entity/
    Γöé   Γöé               Γö£ΓöÇΓöÇ repository/
    Γöé   Γöé               Γö£ΓöÇΓöÇ service/
    Γöé   Γöé               Γö£ΓöÇΓöÇ security/
    Γöé   Γöé               ΓööΓöÇΓöÇ ...
    Γöé   Γöé
    Γöé   ΓööΓöÇΓöÇ resources/
    Γöé       ΓööΓöÇΓöÇ application.properties
    Γöé
    ΓööΓöÇΓöÇ test/
```

The exact package contents may evolve as new business modules are added.

---

# Frontend Structure

The frontend uses Next.js App Router architecture.

```text
frontend/
ΓööΓöÇΓöÇ src/
    Γö£ΓöÇΓöÇ app/
    Γöé   Γö£ΓöÇΓöÇ (dashboard)/
    Γöé   Γöé   Γö£ΓöÇΓöÇ sales/
    Γöé   Γöé   Γöé   ΓööΓöÇΓöÇ pos/
    Γöé   Γöé   ΓööΓöÇΓöÇ ...
    Γöé   Γö£ΓöÇΓöÇ login/
    Γöé   Γö£ΓöÇΓöÇ layout.tsx
    Γöé   ΓööΓöÇΓöÇ ...
    Γöé
    Γö£ΓöÇΓöÇ components/
    Γö£ΓöÇΓöÇ contexts/
    Γö£ΓöÇΓöÇ providers/
    Γö£ΓöÇΓöÇ lib/
    Γöé   ΓööΓöÇΓöÇ api/
    Γöé       ΓööΓöÇΓöÇ apiClient.ts
    Γö£ΓöÇΓöÇ store/
    ΓööΓöÇΓöÇ tests/
```

The frontend uses:

- Next.js App Router
- React components
- Context providers
- API client abstraction
- Client-side state management
- React Query
- Zustand
- Form validation
- Automated tests

---

# Frontend API Client

The frontend uses Axios as its HTTP client.

The main API client is located at:

```text
frontend/src/lib/api/apiClient.ts
```

The API base URL is configured using:

```text
NEXT_PUBLIC_API_BASE_URL
```

A second variable, `API_INTERNAL_BASE_URL`, is **server-side only**. Inside
Docker Compose the backend is reachable at `backend:8080`, a hostname the
browser cannot resolve, so Next.js server components use this one while the
browser uses the public URL. It falls back to `NEXT_PUBLIC_API_BASE_URL` when
unset. Neither variable should ever carry a secret: both are inlined into the
client bundle, which is why they carry URLs and nothing else.

> **Set `NEXT_PUBLIC_API_BASE_URL` for anyone who is not on the API's own
> machine.** The value is compiled into the browser bundle, so `localhost`
> means *the browser's machine*, not the server. A colleague opening the app
> from their own desk, or a phone on the shop network, gets
> `net::ERR_CONNECTION_REFUSED` on every request and no explanation, because
> nothing about the page looks wrong.
>
> Set it to the address that machine can actually reach — the host's LAN IP
> (`http://192.168.x.x:8081/api/v1`) for local network use, or the public API
> URL for a deployed frontend — and restart the frontend so the new value is
> rebuilt into the bundle. Editing the value without rebuilding changes
> nothing.
>
> When it is left unset entirely, the client derives the API address from the
> page's own origin, which works from any machine on the same host.

The client automatically attaches the JWT token to authenticated requests.

Example:

```http
Authorization: Bearer <JWT_TOKEN>
```

When a `401 Unauthorized` response is received, the frontend clears the stored authentication information and redirects the user to the login page.

---

# Database Model

The main business entities include:

```text
User
Role
Permission
Product
Category
Supplier
Customer
```

The core inventory relationship can be represented as:

```text
Category
   |
   +------ Product
             |
             +------ Stock Information
```

Business relationships can be extended toward:

```text
Supplier
   |
   +------ Products
             |
             v
          Inventory
             |
             v
          Customer
```

---

# Core Database Entities

## User

Stores application users and their organizational information.

## Role

Defines the role assigned to a user.

## Permission

Defines specific actions that can be performed within the system.

## Product

Stores product and inventory information.

## Category

Organizes products into hierarchical groups.

## Supplier

Stores supplier and supplier-account information.

## Customer

Stores customer and customer-account information.

---

# API Authentication Flow

```text
User
 |
 | Login
 v
Next.js
 |
 | POST /api/v1/auth/login
 v
Spring Boot
 |
 | Validate credentials
 v
User Service
 |
 | Check password
 v
BCrypt
 |
 | Valid
 v
JWT Generated
 |
 v
Frontend
 |
 | Store JWT
 v
Authenticated Application
 |
 | Authorization: Bearer JWT
 v
Protected API
```

---

# Example Product Flow

```text
User
 |
 | Create Product
 v
Frontend
 |
 | POST /api/v1/products
 v
JWT Authentication
 |
 v
Authorization
 |
 v
ProductController
 |
 v
ProductService
 |
 v
ProductRepository
 |
 v
PostgreSQL
```

---

# Local Development Setup

## Prerequisites

Install:

- Java 17
- Maven 3.9+
- Node.js 20+
- npm
- PostgreSQL
- Git
- Docker Desktop (optional but recommended)

Verify:

```bash
java -version
mvn -version
node -version
npm -version
git --version
```

---

# Clone the Repository

```bash
git clone https://github.com/yemom/inventory-system.git
cd inventory-system
```

---

# Backend Setup

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

Backend:

```text
http://localhost:8081
```

API:

```text
http://localhost:8081/api/v1
```

---

# Frontend Setup

Open another terminal:

```bash
cd frontend
npm install
npm run dev
```

Frontend:

```text
http://localhost:3000
```

Create:

```text
frontend/.env.local
```

Add:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api/v1
```

---

# Running the Backend

The backend needs PostgreSQL. Docker is the quickest way to have one:

```bash
docker run -d --name stockflow-db `
  -e POSTGRES_DB=stockflow_db `
  -e POSTGRES_USER=stockflow `
  -e POSTGRES_PASSWORD=stockflow `
  -p 5432:5432 postgres:16-alpine
```

Create `backend/.env` (copy `.env-example` at the repository root) and then run:

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

| URL | Purpose |
|---|---|
| `http://localhost:8081/actuator/health` | Health check |
| `http://localhost:8081/api/v1` | API root |
| `http://localhost:8081/swagger-ui.html` | API documentation, if enabled |

The application **refuses to start** with the development default `JWT_SECRET`.
That is deliberate: a signing key committed to the repository lets anyone forge an
administrator token. Generate one:

```bash
openssl rand -hex 32
```

Redis is optional. Without it, rate limiting and caching degrade to in-process
equivalents rather than shared ones, which is fine for a single-instance
development run.

## Database migrations

Hibernate is configured with `ddl-auto=update`, so the schema is derived from the
entities on start-up. There is no migration tool and therefore no migration
history — a change to an entity alters the schema in place, which is adequate for
development and would need Flyway or Liquibase before a production deployment
with data in it.

## Running the tests

```bash
mvn test
```

Integration tests use Testcontainers and therefore need a working Docker daemon.
If Docker Desktop is not running they will appear to hang rather than fail, which
is the first thing to check when the suite does not finish.

---

# Running the Frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend:

```text
http://localhost:3005
```

The dev server points at `http://localhost:8081/api/v1`. To change it, or to add
the public build-time values, create `frontend/.env.local`:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api/v1
NEXT_PUBLIC_APP_NAME=StockFlow
NEXT_PUBLIC_APP_URL=http://localhost:3005
```

`NEXT_PUBLIC_*` variables are inlined into the client bundle at **build** time, so
changing one requires a restart of the dev server or a rebuild. They are not
secrets: anything with this prefix is visible to every browser that loads the app.

`frontend/.env.local` exists and already contains working local values.

| Command | Purpose |
|---|---|
| `npm run dev` | Development server with hot reload |
| `npm run type-check` | TypeScript, no emit |
| `npm run lint` | ESLint |
| `npm test` | Jest, watch mode |
| `npm test -- --ci` | Jest, single run |
| `npm run build` | Production build |
| `npm run start` | Serve the production build |

---
# Running with Docker

From the repository root:

```bash
docker compose up --build
```

Or:

```bash
docker-compose up --build
```

Services:

```text
PostgreSQL
Backend
Frontend
```

With Docker:

```text
Frontend:  http://localhost:3005
Backend:   http://localhost:8081
API:       http://localhost:8081/api/v1
PostgreSQL: localhost:5434
```

---

# Docker Commands

Start:

```bash
docker compose up
```

Build and start:

```bash
docker compose up --build
```

Background:

```bash
docker compose up -d
```

Stop:

```bash
docker compose down
```

Stop and remove volumes:

```bash
docker compose down -v
```

View containers:

```bash
docker ps
```

View logs:

```bash
docker compose logs
```

Backend logs:

```bash
docker compose logs backend
```

Frontend logs:

```bash
docker compose logs frontend
```

Database logs:

```bash
docker compose logs db
```

---

# Environment Configuration

Example frontend:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api/v1
```

Backend configuration should include values such as:

```env
DATABASE_URL=jdbc:postgresql://localhost:5434/stockflow_db
DATABASE_USERNAME=stockflow
DATABASE_PASSWORD=your-secure-password
JWT_SECRET=your-secure-jwt-secret
JWT_EXPIRATION=86400000
```

Production credentials should never be committed to Git.

---

# Testing

## Results

These are the actual results of a full run, not an expectation.

| Suite | Command | Result |
|---|---|---|
| Backend | `mvn -B test` | **448 tests - 0 failures, 0 errors** |
| Frontend | `npx jest --ci` | **222 tests, 21 suites - all passing** |
| Frontend types | `npx tsc --noEmit` | **clean** |
| Frontend lint | `npm run lint` | **0 errors**, 55 warnings, all in pre-existing files |
| Frontend build | `npm run build` | **compiles**, routes prerendered |

Run the two suites **one at a time**. Both fork workers and together they exceed
the machine's memory: the backend's forked JVM dies with "The forked VM
terminated without properly saying goodbye", and jest workers fail with heap
allocation errors. Neither is a test failure — both are resource exhaustion, and
both pass when run alone.

The backend integration tests need a working Docker daemon for Testcontainers.
Docker Desktop being stopped is the most common reason they appear to hang.

### Two things that will waste your time if you do not know them

**Currency is formatted with a non-breaking space.** `formatCurrency(750)` returns
`ETB\u00A0750.00` — the character between `ETB` and the digits is `U+00A0`, not
`U+0020`. Testing Library's exact string matcher does not normalise it, so
`screen.getByText(formatCurrency(750))` fails even when the text is on screen and
character-for-character correct. Assert with a pattern (`getByText(/750\.00/)`)
or look the element up by its row label instead. Do not "fix" this by changing
`formatCurrency`; the output is correct and the matcher is the limitation.

**A nullable query parameter needs an explicit cast.** In a JPQL predicate like
`:search IS NULL OR LOWER(s.orderNumber) LIKE ...`, Hibernate has nothing to
infer the parameter's type from and binds the null as `bytea`. PostgreSQL then
refuses the whole query with `function lower(bytea) does not exist` — and only
when the parameter happens to be null, so it looks like an intermittent failure.
Write `CAST(:search AS string)`.

## Backend

The backend uses:

- JUnit
- Mockito
- Spring Boot Test
- Spring Security Test
- Testcontainers

Run:

```bash
cd backend
mvn test
```

Complete build:

```bash
mvn clean verify
```

What is covered:

| Area | Where |
|---|---|
| Financial formulas, costing, COGS, margins, balances | `FinancialCalculationsTest` (45 tests, pure) |
| Forecasting, model selection, backtesting, intervals, robust z-score | `TimeSeriesTest` (44 tests, pure) |
| Reports, exports, formula-injection neutralisation, row cap | `ReportAndExportIntegrationTest` |
| Sales persistence and payment recording | `SalesPersistenceIntegrationTest` |
| Purchase receipt, supplier balances, warehouses, transfers | `PurchasingAndWarehouseIntegrationTest` |
| Returns, refunds, expenses | `ReturnsAndExpensesIntegrationTest` |
| Forecasting, anomalies, inventory advice, assistant, authorisation boundaries | `AiServiceIntegrationTest` (31 tests) |
| Chatbot: role-by-role permission matrix, customer isolation, figures matching the reports, no-write requests, injection, rate limit, retention | `ChatbotServiceIntegrationTest` (22 tests) |
| Chatbot provider: both URL shapes, bearer auth, model-availability check, zero outbound when disabled, every failure mode, no key in any message | `OpenAiCompatibleProviderTest` (18 tests, HTTP mocked) |
| Chatbot frontend: both channels and their auth, no provider key in any request, expired-session vs rate-limit typing, stored-session TTL, thread loading/empty/error/retry, safe rendering | `chatbotApi.test.ts`, `chatThread.test.tsx` (30 tests) |
| Approval workflows and permission enforcement | Existing integration suites |

## Test data

Test data is written by fixtures that stamp `TESTDATA-` on every order number and
payment reference they create, and that exist only under `src/test/java` - never
on the application's classpath in a deployment. `TradingHistoryFixture` is the one
that writes a season of trading for the model evaluation suite; it writes rows
directly rather than calling the API, because going through `POST /sales` twenty
times a day runs the whole filter chain per sale and took the suite past fifteen
minutes.

## Frontend

The frontend uses:

- Jest
- React Testing Library
- jest-dom
- jsdom

Run:

```bash
cd frontend
npm test
```

Watch mode:

```bash
npm test -- --watch
```

Type checking:

```bash
npm run type-check
```

Linting:

```bash
npm run lint
```

Production build:

```bash
npm run build
```

`financePayables.test.tsx` is worth reading as an example of the testing
philosophy: it was rewritten when the page stopped deriving balances in the
browser. It now pins that the page renders what the server computed, that goods
in transit are not a payable, and that a recorded payment names an order rather
than an amount the client chose.

---
# Jest TypeScript Configuration

If TypeScript reports:

```text
Cannot find name 'describe'
Cannot find name 'it'
Cannot find name 'expect'
Cannot find name 'jest'
```

install:

```bash
npm install --save-dev @types/jest
```

Then ensure Jest types are available to the TypeScript configuration.

---

# Build and Production

Build the frontend:

```bash
npm run build
```

Start production:

```bash
npm start
```

Run type checking:

```bash
npm run type-check
```

Run linting:

```bash
npm run lint
```

---

# Security

StockFlow uses:

- JWT authentication
- BCrypt password hashing
- Role-based access control
- Permission-based authorization
- Stateless Spring Security sessions
- CORS configuration
- Protected API endpoints

## Security Recommendation

Never commit real production secrets.

Protect:

```text
Database passwords
JWT secrets
Administrator credentials
API keys
Cloud credentials
Service account credentials
```

Use environment variables, deployment secrets, Docker secrets, or a dedicated secret-management system.

If real credentials have previously been committed to a public repository, rotate them.

---

# Development Guidelines

## Backend

Maintain:

```text
Controller
    |
    v
Service
    |
    v
Repository
```

Controllers should handle HTTP concerns.

Services should contain business logic.

Repositories should handle persistence.

Security logic should remain in the security layer.

## Frontend

Keep API communication centralized through the API client.

Prefer:

```text
Component
    |
    v
Hook / Query
    |
    v
API Client
    |
    v
Backend
```

Use TypeScript types for API data.

Validate user input before submitting forms.

---

# Git Workflow

Create a feature branch:

```bash
git checkout -b feature/product-management
```

Check changes:

```bash
git status
```

Add:

```bash
git add .
```

Commit:

```bash
git commit -m "feat: improve product management"
```

Push:

```bash
git push origin feature/product-management
```

---

# Commit Convention

Examples:

```text
feat: add product management
fix: resolve JWT authentication issue
fix: correct product stock validation
refactor: improve inventory service
test: add product service tests
docs: update project documentation
```

---

# Production Considerations

Before production deployment, configure:

- Production PostgreSQL
- Strong JWT secret
- Secure administrator credentials
- HTTPS
- Secure CORS
- Environment-specific configuration
- Database backups
- Monitoring
- Centralized logging
- Error handling
- Rate limiting
- Audit logging
- Database migrations
- Secure secret management

The current backend uses Hibernate automatic schema updates for development.

For production, use controlled database migrations such as Flyway or Liquibase.

---

# Future Improvements

## Sales Transaction Persistence

Implement:

- Sales orders
- Sale items
- Transaction numbers
- Payment records
- Payment status
- Customer association
- Discounts
- VAT
- Change
- Receipts

Architecture:

```text
Sale
 |
 +-- Sale Items
 |
 +-- Customer
 |
 +-- Payment
 |
 +-- User/Cashier
```

## Purchase Management

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
Inventory Increase
```

## Stock Transfers

Support transfers between:

- Branches
- Warehouses
- Storage locations

## Returns

Implement:

- Customer returns
- Supplier returns
- Return reasons
- Returned quantities
- Inventory adjustments
- Refund information

## Damaged and Expired Products

Support:

- Damaged products
- Expired products
- Lost products
- Stock corrections

## Financial Management

Add:

- Expenses
- Payments
- Receivables
- Payables
- Profit and loss
- Cash flow
- Financial reports

## Advanced Reporting

Add reports for:

- Daily sales
- Monthly sales
- Product performance
- Inventory valuation
- Low-stock products
- Supplier performance
- Customer activity
- Profit and loss
- Expenses
- Purchase history
- Sales history

## Audit Logging

Add audit records containing:

- User
- Action
- Entity
- Entity ID
- Timestamp
- Previous value
- New value
- IP address where appropriate

---

# Known Implementation Notes

## POS Transaction Persistence

The current POS checkout flow creates checkout and receipt information on the frontend.

A completed sale is not currently persisted through a dedicated backend sales transaction API.

The POS should therefore currently be considered a frontend checkout workflow rather than a complete persisted sales transaction system.

## Database Schema Management

The backend currently uses Hibernate automatic schema updates for development.

Production should use a controlled migration strategy.

## Root Package Lock

The frontend has its own package management files.

A root-level Node.js lockfile should only be maintained if the repository intentionally uses a root Node workspace.

---

# Troubleshooting

## Backend Cannot Connect to PostgreSQL

Check:

```bash
docker compose ps
```

Database logs:

```bash
docker compose logs db
```

Verify the database URL and credentials.

## Frontend Cannot Connect to Backend

Check:

```env
NEXT_PUBLIC_API_BASE_URL=http://localhost:8081/api/v1
```

Verify:

```text
http://localhost:8081
```

Also check browser developer tools for:

- CORS errors
- 401 errors
- 403 errors
- 404 errors
- Network failures

## JWT Authentication Problems

Verify:

```http
Authorization: Bearer <TOKEN>
```

Check:

- Token validity
- Token expiration
- User existence
- JWT secret
- User permissions

## TypeScript Errors

Run:

```bash
npm run type-check
```

If dependencies are corrupted:

```bash
rm -rf node_modules
npm install
```

On Windows PowerShell:

```powershell
Remove-Item -Recurse -Force node_modules
npm install
```

## Frontend Build Problems

Run:

```bash
npm install
npm run type-check
npm run lint
npm run build
```

---

# Verification Checklist

```text
[ ] PostgreSQL is running
[ ] Backend starts successfully
[ ] Frontend starts successfully
[ ] Database connection works
[ ] Login works
[ ] JWT token is generated
[ ] Protected API requests work
[ ] Product creation works
[ ] Product search works
[ ] Product update works
[ ] Product deactivation works
[ ] Category management works
[ ] Supplier management works
[ ] Customer management works
[ ] Inventory information is displayed
[ ] POS interface works
[ ] TypeScript passes
[ ] Tests pass
[ ] Lint passes
[ ] Production build succeeds
```

---

# Recommended Development Workflow

```text
1. Understand the business requirement
        |
        v
2. Design the database/entity model
        |
        v
3. Create DTOs
        |
        v
4. Implement repository
        |
        v
5. Implement service
        |
        v
6. Implement controller
        |
        v
7. Add security permissions
        |
        v
8. Add frontend API integration
        |
        v
9. Build frontend UI
        |
        v
10. Add tests
        |
        v
11. Run type-check
        |
        v
12. Run lint
        |
        v
13. Run backend tests
        |
        v
14. Build frontend
        |
        v
15. Test complete workflow
```

---

# Deployment

The application is built to run in two places: the Spring Boot API on **Render**
and the Next.js frontend on **Vercel**. Both are configured from environment
variables; nothing secret belongs in the repository.

## What is deployed where

| Piece | Host | Built from | Reachable at |
|---|---|---|---|
| API (Spring Boot) | Render web service | `backend/Dockerfile` | `https://inventory-system-pm18.onrender.com` |
| Frontend (Next.js) | Vercel | `frontend/` (auto-detected Next.js build) | your Vercel domain |
| PostgreSQL | Render managed database (`stockflow-db`) | — | private network |
| Redis | Render managed Redis | — | private network |

`render.yaml` in this repository declares the whole backend stack — database,
Redis, API and health check — so a new Render service can be created straight
from the blueprint. The frontend is not in that file: Vercel deploys it from
this Git repository using its own Next.js preset.

## Backend environment variables (Render)

Set these in the Render service's **Environment** tab. The ones marked *secret*
must never be committed and must never appear in the frontend.

| Variable | Required | Example | Notes |
|---|---|---|---|
| `DATABASE_URL` | *secret* | `postgresql://stockflow:…@dpg-…/stockflow_db` | Render injects this when the service is linked to the database |
| `DATABASE_USERNAME` | *secret* | `stockflow` | |
| `DATABASE_PASSWORD` | *secret* | `…` | |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | no | `redis`, `6379`, `…` | Without them, rate limiting degrades but the app still starts |
| `JWT_SECRET` | *secret* | `openssl rand -hex 32` | **No default.** The backend refuses to start without a strong one |
| `SUPER_ADMIN_USERNAME` / `SUPER_ADMIN_PASSWORD` | *secret* | `admin`, ≥ 12 chars | Seeds the first super-admin on an empty database |
| `CORS_ALLOWED_ORIGINS` | yes in production | `https://your-app.vercel.app` | Comma-separated exact origins; without it the browser reports an opaque network error |
| `CHATBOT_PROVIDER` | no | `deterministic` | `deterministic` (default, no outbound request) or `openai-compatible` |
| `CHATBOT_API_KEY` or `OPENAI_API_KEY` | no | `sk-…` | Server-side secret for the optional phrasing layer only |
| `CHATBOT_LLM_PHRASED` | no | `true` | `false` guarantees zero outbound LLM requests |

See `.env-example` for the complete list with safe placeholders, and
[docs/deployment.md](docs/deployment.md) for the full reference.

## Frontend environment variables (Vercel)

| Variable | Required | Value | Notes |
|---|---|---|---|
| `NEXT_PUBLIC_API_BASE_URL` | **yes** | `https://inventory-system-pm18.onrender.com/api/v1` | Baked into the browser bundle at **build time** |
| `NEXT_PUBLIC_APP_URL` | no | `https://your-app.vercel.app` | Used for absolute links |

> **The one thing to get right.** `NEXT_PUBLIC_API_BASE_URL` is inlined when the
> site is built, not when it is served, so changing it in the dashboard requires
> a **redeploy**, not just a restart. If it is not set, the client falls back to
> `http://localhost:8081/api/v1` — which is right for `docker compose up` and
> wrong for anything else: every request from a user's browser would go to their
> own machine. Nothing about it is secret; it is public by construction.

## Health checks

The API exposes `GET /actuator/health` (and `GET /api/v1/dashboard/health`).
Point Render's health check path at `/actuator/health`. The first request after
a long idle period can take 30–60s while the instance scales up — the frontend's
request timeout is 60s for exactly this reason.

## Manual actions a dashboard requires

These cannot be done from the repository and must be set once per environment:

1. Create the Render blueprint from `render.yaml`, or create the service by hand.
2. Link the PostgreSQL database and Redis to the service (Render does not pass
   them automatically unless the blueprint is used).
3. Set `JWT_SECRET` and `SUPER_ADMIN_PASSWORD` before the first deploy — the
   backend refuses to start without them.
4. Set `CORS_ALLOWED_ORIGINS` to the exact Vercel domain.
5. In Vercel, set `NEXT_PUBLIC_API_BASE_URL` to the Render API origin and
   redeploy.

## Verifying a deployment

```bash
curl -s https://inventory-system-pm18.onrender.com/actuator/health
curl -s "https://inventory-system-pm18.onrender.com/api/v1/dashboard/stats"   # 401 without a token
```

No live deployment was verified while preparing this documentation; treat the
URLs above as configuration, not as a claim that a deploy has been tested.

---

# Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request:

| Job | Runs |
|---|---|
| `backend` | `mvn -B clean compile`, then `mvn -B clean test` (Testcontainers starts its own PostgreSQL) |
| `frontend` | `npm ci`, `npm run type-check`, `npm run lint`, `npx jest --ci`, `npm run build` |
| `docker` | `docker build` for both images |

No job needs production credentials: the integration tests provision their own
database, and the frontend build only inlines the API URL it is given. Workflow
runs on GitHub-hosted runners with the Docker daemon available, which is what
Testcontainers needs.

---

# Quality Standards

Changes to StockFlow should aim to maintain:

- Clean architecture
- Separation of concerns
- Type safety
- Secure authentication
- Proper authorization
- Input validation
- Consistent API responses
- Meaningful error handling
- Automated testing
- Maintainable code
- Reusable frontend components
- Clear database relationships
- Production-ready configuration

---

# Project Status

All phases are complete and tested. What follows is what was actually done, and
what remains genuinely unfinished.

## What is implemented and verified

**Transactions are persisted and audited.** Sales, purchases, payments, expenses
and returns are real records with transaction dates, actors and approval
workflows. Stock moves only through `StockLedgerService`, in one transaction, and
every movement row is the sole record of how a quantity changed.

**Inventory is per warehouse.** `WarehouseStock` holds quantity and average cost
per warehouse. `Product.quantity` is republished from those rows so every existing
screen keeps working, but the warehouse rows are the record.

**Costing is a documented moving weighted average**, recalculated on every goods
receipt, **frozen onto each sale line at the point of sale**. A later price change
cannot rewrite historical profit and a closed period cannot be restated.

**Financial calculations live in one place.** `FinancialCalculations` is pure and
unit-tested. Revenue is net of returns and excludes sales tax; COGS comes from the
frozen cost; cash comes from the payment ledger, never from invoices; expenses are
approved claims only. See
[docs/financial-calculations.md](docs/financial-calculations.md).

**Fourteen reports**, all from validated records, all with date filters, all
computed by one service, all exportable to CSV or PDF rendered server-side from the
same payload the screen showed.

**Read-only intelligence.** Forecasting, anomaly detection, inventory advice,
financial explanations and a business assistant. No external provider, no
credentials, no outbound request, and no write method in the package. Model
versions and every assistant question are logged. See
[docs/intelligence.md](docs/intelligence.md).

**The business chatbot answers from those same figures — deterministically
first.** With the shipped defaults it makes no outbound request and needs no
key. An *optional* OpenAI-compatible model may re-phrase tool results that have
already been computed; its wording is checked number-by-number against the tool
output before anyone reads it, the model is verified against the account at
startup, the key lives server-side only (never in the frontend, an API
response or a log), `CHATBOT_LLM_PHRASED=false` guarantees zero outbound calls,
and every failure — timeout, rate limit, HTTP error, malformed response,
unavailable model — falls back to the deterministic reply. See
[docs/chatbot.md](docs/chatbot.md).

**Authorization was tightened where it was wrong.** Financial reporting moved off
`DASHBOARD_VIEW` - which every role holds - onto `FINANCIAL_REPORT_VIEW`,
`PROFIT_LOSS_READ`, `RECEIVABLE_READ` and `PAYABLE_READ`. AI permissions are
read-only by construction.

**Tests: 448 backend, 222 frontend, all passing**, plus a clean type-check, lint
and production build. Actual results are under [Testing](#testing).

## Known limitations

These are real and are stated rather than hidden:

| Limitation | Why it is acceptable here |
|---|---|
| **No lot or batch tracking** | Goods are fungible retail, so costing is a moving weighted average. FIFO would need data that is not captured. |
| **Supplier lead times are a default** | Not yet measured from order dates. Every recommendation says it assumed `AI_DEFAULT_LEAD_TIME_DAYS` rather than passing it off as measured. |
| **Seasonality is weekly only** | Annual seasonality needs years of history this data does not yet have. The model is documented and would extend. |
| **No multi-currency arithmetic** | `currency` is carried on orders for provenance; conversion is not performed. |
| **Forecasts are computed per request** | Fast at this volume and always current. A scheduled job with cached results is a next step for a large deployment, not a correction. |
| **Hand-written CSV rather than a library** | RFC 4180 is a quoting rule and a line ending. Tested against the specification rather than against a dependency's behaviour. |
| **The dashboard is one page, not a workspace** | The stated requirements are met. Widget-level drag-and-drop customisation is a product decision, not a correctness one. |

Nothing in this list is a placeholder. Every figure shown comes from a persisted
transaction, and every claim made by the intelligence layer is traceable to the
records and the model that produced it.
---

# Contribution

To contribute:

1. Fork the repository.
2. Create a feature branch.
3. Implement the feature or fix.
4. Add or update tests.
5. Run type checking.
6. Run linting.
7. Run backend tests.
8. Build the frontend.
9. Commit your changes.
10. Push the branch.
11. Create a pull request.

Example:

```bash
git checkout -b feature/new-feature
git add .
git commit -m "feat: implement new feature"
git push origin feature/new-feature
```

---

# Author

## Esrom Basazinaw

Software Engineering Student  
Addis Ababa Institute of Technology

GitHub:

https://github.com/yemom

Portfolio:
https://esrom12-portfolio.vercel.app/

---

# Repository

GitHub Repository:

https://github.com/yemom/inventory-system

---

# Files Changed

The work below is uncommitted. `git status` shows it; nothing has been added or
committed.

## New backend - finance

| File | Purpose |
|---|---|
| `finance/FinancialCalculations.java` | Every formula. Pure, static, no database, no clock. |
| `finance/FinancialCalculationService.java` | Assembles `FinancialSummary`; one fetch per source record. |
| `finance/FinancialSummary.java` | The record every report, dashboard and assistant reads. |

## New backend - intelligence

| File | Purpose |
|---|---|
| `ai/TimeSeries.java` | All models and statistics, pure and tested. |
| `ai/AiProperties.java` | Intelligible configuration, every field documented inline. |
| `ai/AiModelRegistry.java` | Reads and supersedes trained model versions. |
| `ai/ForecastingService.java` | Model selection by backtest, prediction intervals, refusal on insufficient data. |
| `ai/AnomalyDetectionService.java` | Revenue, expense and stock alerts, with evidence. |
| `ai/InventoryOptimizationService.java` | Reorder points, safety stock, stock-out projection. |
| `ai/FinancialInsightService.java` | Explanations quoting the calculation layer. |
| `ai/BusinessAssistantService.java` | Intent classification, per-intent permission checks, query logging. |
| `model/AiModelVersion.java` | Training period, sample size, measured error, candidate scores. |
| `model/AiQueryLog.java` | Every question, intent, parameters, outcome and duration. |

## New backend - data and services

| File | Purpose |
|---|---|
| `model/WarehouseStock.java` | Per-warehouse quantity and weighted-average cost. |
| `model/ReturnOrder.java`, `ReturnOrderItem.java` | Returns as documents, not as `localStorage`. |
| `service/WarehouseStockService.java` | Locking, weighted-average cost, `Product.quantity` republish. |
| `service/StockLedgerService.java` | The only place a `StockMovement` row is written. |
| `service/StockShortfallPolicy.java` | Clamp for approved counts, refuse for operational moves. |
| `service/FinanceService.java` | Validated cash: payments cannot be free-standing. |
| `service/ReturnService.java` | Raise, then approve; approving posts stock and finance together. |
| `service/ReportService.java` | The fourteen reports, one shape. |
| `service/ExportService.java` | Server-side CSV and PDF, formula injection neutralised. |
| `controller/AiController.java`, `ReportController.java`, `FinanceController.java`, `ReturnController.java` | New endpoints. |
| Repository queries | `sumNetSalesPerDay`, `findOutstandingReceivables`, `findOutstandingPayables`, `findLossesBetween` and the supporting queries. |

## Extended

| File | What changed |
|---|---|
| `model/SaleOrder` | `warehouse`, `amountPaid`, `transactionDate`, `currency`, `costOfGoodsSold`. |
| `model/SaleOrderItem` | `unitCost` (frozen at sale), `quantityReturned`. |
| `model/PurchaseOrder` | `supplier`, `warehouse`, statuses, terms, due dates. |
| `model/PurchaseOrderItem` | `quantityReceived`, `quantityReturned`. |
| `model/StockMovement` | `destinationWarehouse`, `reason`, `movementGroup`, `unitCost`, precise types. |
| `model/Payment` | `direction`, `partyType`, document links, `transactionDate`, `externalReference`. |
| `model/Expense` | `transactionDate`, `approver`. |
| `security/RolePermissionCatalog.java` | Financial permissions moved off `DASHBOARD_VIEW`; five read-only AI permissions added. |
| `controller/DashboardController.java` | Split into `/stats`, `/financial`, `/insights` with separate permissions. |
| `controller/PaymentController.java`, `ExpenseController.java`, `PurchaseOrderController.java`, `StockMovementController.java`, `sales/SalesReportService` | Rewritten over the new services. |
| `pom.xml` | `com.github.librepdf:openpdf:1.3.30`. |
| `application.properties` | `app.ai.*` and `app.report.*` blocks, every field documented inline. |

## New frontend

| File | Purpose |
|---|---|
| `lib/api/aiApi.ts` | Intelligence, finance, dashboard, reports and returns clients. |
| `hooks/useAiDashboard.ts` | Gated queries, disabled rather than fired-and-refused. |
| `components/ai/AiInsightPanel.tsx` | Insights, alerts and recommendations with their evidence. |
| `components/ai/ForecastChart.tsx` | History and forecast on one axis, with the prediction band. |
| `components/ai/AssistantPanel.tsx` | The assistant, with period, assumptions and source links. |
| `components/ui/DateRangeFilter.tsx` | Calendar-month presets, not rolling approximations. |
| `components/reports/ReportScreen.tsx` | One component rendering any report, with export and pagination. |
| `app/(dashboard)/dashboard/page.tsx` | Rewritten on `/dashboard/financial` and `/dashboard/insights`. |
| `app/(dashboard)/finance/profit-loss/page.tsx` | Now the backend statement. |
| `app/(dashboard)/finance/receivables/page.tsx`, `payables/page.tsx` | Now the server's balances, with the collection flow kept. |
| `app/(dashboard)/sales/returns/page.tsx`, `purchases/returns/page.tsx` | `localStorage` replaced by server documents with approval. |
| `app/(dashboard)/reports/{sales,purchases,financial,inventory,inventory-value}` | Now `ReportScreen`. |
| `app/(dashboard)/reports/ai/page.tsx` and `reports/ai/{models,queries}` | Forecast and alerts, model evaluation, query log. |

## Rewritten frontend tests

`financePayables.test.tsx` was rewritten when the page stopped deriving balances
in the browser. It previously pinned the client-side arithmetic that produced the
wrong figure - it asserted that the page subtracted `paid` from `totalAmount`
itself, which is the behaviour that made a paginated list an under-count. It now
pins that the page renders what the server computed, that goods in transit are not
a payable, and that a payment names an order rather than an amount the client
chose.

## New and rewritten documentation

| Document | Covers |
|---|---|
| `docs/architecture.md` | How the pieces fit, the data model, the security model, the limitations. |
| `docs/financial-calculations.md` | Every formula, the costing method, the accounting policies, what is not counted. |
| `docs/intelligence.md` | How the models work and how they are evaluated. |
| `docs/roles-and-permissions.md` | Extended with the financial and AI permission boundaries. |
| `README.md` | Rewritten sections: Business Intelligence, Reports and Exports, Testing, Project Status, Project Structure, this list. |
| `.env-example` | `AI_*` and `REPORT_EXPORT_MAX_ROWS`, every value explained. |
| `docker-compose.yml` | New `AI_*` variables; committed super-admin credentials removed. |
| `render.yaml` | New `AI_*` variables, with a note on why there is no API key. |

## Security fix worth calling out

`docker-compose.yml` committed working super-admin credentials, so every
deployment started from this file booted with the same administrator account and
password. They now come from `.env` with no default at all, so an unset variable
fails the start-up instead of leaving a known account reachable. `.env` itself is
and was gitignored.

---

# License

This project is currently maintained as a software engineering project.

Add an appropriate open-source license such as MIT, Apache 2.0, or another license if the project is intended for public distribution.

---

# Final Architecture Overview

```text
                         STOCKFLOW
                             |
       +---------------------+---------------------+
       |                                           |
       v                                           v
   FRONTEND                                    BACKEND
   Next.js                                  Spring Boot
       |                                           |
   React UI                                  Controllers
       |                                           |
   React Query                                  Services
       |                                           |
   Zustand                                    Repositories
       |                                           |
   Axios API                                      |
       |                                           |
       +-------------------+-----------------------+
                           |
                           v
                      PostgreSQL
                           |
                           v
                    Business Data
```

StockFlow provides the foundation for a secure, scalable inventory and business management platform, with the architecture designed to support additional transaction, reporting, financial, and operational modules as development continues.
