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
- [Testing](#testing)
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
├── ID
├── SKU
├── Barcode
├── Name
├── Unit
├── Description
├── Category
├── Purchase Price
├── Selling Price
├── Minimum Selling Price
├── Minimum Stock
├── Maximum Stock
├── Reorder Level
├── Quantity
├── Batch Tracking
├── Expiry Tracking
├── Active Status
├── Created At
└── Updated At
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
├── Supplier Number
├── Company Name
├── Contact Person
├── Phone
├── Email
├── Address
├── Tax Number
├── Payment Terms
├── Outstanding Balance
├── Status
└── Notes
```

The system supports creating, retrieving, searching, and updating suppliers.

---

# Customer Management

Customers represent people or organizations purchasing products.

Customer information includes:

```text
Customer
├── Customer Number
├── Name
├── Phone
├── Email
├── Address
├── Customer Type
├── Credit Limit
├── Outstanding Balance
├── Status
└── Notes
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

The backend contains product inventory aggregation functionality that can support dashboard information such as:

- Total active products
- Low-stock products
- Out-of-stock products
- Total inventory value

The frontend uses visualization components such as Recharts for dashboard and reporting interfaces.

Additional business reporting functionality can be extended as transaction persistence is implemented.

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

```text
inventory-system/
│
├── backend/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/
│   │   │   └── resources/
│   │   │
│   │   └── test/
│   │
│   ├── pom.xml
│   ├── Dockerfile
│   └── ...
│
├── frontend/
│   ├── public/
│   ├── src/
│   │   ├── app/
│   │   ├── components/
│   │   ├── contexts/
│   │   ├── lib/
│   │   ├── providers/
│   │   ├── store/
│   │   └── tests/
│   │
│   ├── package.json
│   ├── package-lock.json
│   ├── Dockerfile
│   ├── next.config.*
│   ├── tsconfig.json
│   └── ...
│
├── .local-pg/
├── docker-compose.yml
├── .gitignore
└── README.md
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
└── src/
    ├── main/
    │   ├── java/
    │   │   └── com/
    │   │       └── inventory/
    │   │           └── backend/
    │   │               │
    │   │               ├── BackendApplication.java
    │   │               ├── config/
    │   │               ├── controller/
    │   │               ├── dto/
    │   │               ├── entity/
    │   │               ├── repository/
    │   │               ├── service/
    │   │               ├── security/
    │   │               └── ...
    │   │
    │   └── resources/
    │       └── application.properties
    │
    └── test/
```

The exact package contents may evolve as new business modules are added.

---

# Frontend Structure

The frontend uses Next.js App Router architecture.

```text
frontend/
└── src/
    ├── app/
    │   ├── (dashboard)/
    │   │   ├── sales/
    │   │   │   └── pos/
    │   │   └── ...
    │   ├── login/
    │   ├── layout.tsx
    │   └── ...
    │
    ├── components/
    ├── contexts/
    ├── providers/
    ├── lib/
    │   └── api/
    │       └── apiClient.ts
    ├── store/
    └── tests/
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

StockFlow currently provides a working foundation for an inventory management platform with:

- Spring Boot backend
- PostgreSQL database
- JWT authentication
- Role and permission architecture
- Product management
- Category management
- Supplier management
- Customer management
- Inventory calculations
- Next.js frontend
- POS frontend workflow
- Docker Compose configuration
- Backend testing infrastructure
- Frontend testing infrastructure

Some business transaction capabilities, particularly persistent sales transactions and advanced reporting, require additional backend implementation.

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
