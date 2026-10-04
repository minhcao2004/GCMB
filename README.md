# GCMB — GenZ Cinema & Music Box Management System

 is a centralized web application for managing the GenZ Cinema & Music Box chain. It connects room bookings, F&B orders, inventory, equipment, staff, payroll, and financial operations across branches.

The project aims to reduce duplicate data entry and manual reconciliation caused by operational data being spread across KiotViet, spreadsheets, and manual records.

**FPT University Capstone Project · SEP490-G12**  
**Architecture:** Modular Monolith + Feature-based Structure  
**Stack:** React (JavaScript/JSX + plain CSS), Java 17, Spring Boot, PostgreSQL

> This README describes the agreed project structure and development setup. Feature descriptions represent the planned scope, not a completion checklist. The source repository was not supplied for verification; configuration examples below must match the actual `pom.xml`, `package.json`, and API implementation.

## Table of Contents

- [About the Project](#about-the-project)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
- [Project Structure](#project-structure)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Usage](#usage)
- [Testing](#testing)
- [Contributing](#contributing)
- [Documentation](#documentation)
- [Team](#team)
- [License](#license)

## About the Project

GCMB brings branch operations and financial records into one system. Its goals are to prevent conflicting room assignments, standardize operational workflows, maintain transaction history, and provide consistent branch and chain-level reporting.

Access is controlled by both business role and branch scope.

| User | Main responsibilities |
| --- | --- |
| Head Office Manager | Manage branches, shared configuration, access rights, major approvals, and chain reports. |
| Branch Manager | Manage branch rooms, stock, equipment, shifts, attendance, expenses, and daily cash closing. |
| Receptionist | Handle bookings, check-in/out, deposits, payments, F&B orders, and personal shift information. |
| Accountant | Process refunds, funds, expenses, reimbursements, reconciliation, payroll, and financial reports. |
| Customer | View the F&B menu and submit orders using an in-room QR code. |

Customer interaction is limited to in-room F&B ordering. Room reservations received through social channels are entered by staff.

## Features

| ID | Feature | Planned capabilities |
| --- | --- | --- |
| FE-01 | System Administration and Configuration | Accounts, role and branch access, password recovery, audit logs, branches, room categories, rooms, time-based pricing, and combo packages. |
| FE-02 | Booking and Room Operations | Walk-in and advance bookings, availability checks, cancellation, check-in/out, room changes, extensions, no-shows, deposits, refunds, billing, and payments. |
| FE-03 | F&B, Inventory and Equipment Management | Menu and room-linked orders, QR ordering, stock receipts and adjustments, equipment assets, incident reports, repairs, and equipment transfers. |
| FE-04 | Workforce and Payroll Management | Staff records and branch assignments, shifts, attendance, monthly attendance closing, payroll, salary advances, Excel export, and salary payment records. |
| FE-05 | Financial Management and Reporting | Cash closing and reconciliation, funds, expenses, approvals, reimbursements, financial-period closing, dashboards, reports, and tax-accounting data export. |

The current scope excludes a customer booking portal, hardware barcode-scanner integration, statutory tax report generation, recipe/BOM-based ingredient management, content licensing management, and regulatory or safety compliance enforcement.

## Tech Stack

| Area | Technology |
| --- | --- |
| Frontend | React with Create React App, JavaScript, JSX, plain CSS |
| Backend | Java 17, Spring Boot, Maven Wrapper |
| Database | PostgreSQL with version-controlled SQL migration scripts |
| Communication | REST API with JSON |
| External integrations | SePay, email service, cloud storage |
| Version control | Git and GitHub |
| Testing | JUnit and API testing tools such as Postman |
| Deployment target | Docker on VPS/Cloud |

Dependency versions are defined in `backend/pom.xml`, `frontend/package.json`, and the frontend lockfile. Use the versions committed by the team.

## Architecture

GCMS uses a Web Client–Server architecture. The React frontend calls the centralized Spring Boot REST API, and the backend accesses PostgreSQL and external services.

The backend is deployed as one application. Business code is grouped into five modules, each owning its controllers, services, repositories, entities, and DTOs. Frontend features follow the same business boundaries.

- Keep business rules, authorization, and branch-scope checks in backend services.
- Use DTOs for API requests and responses.
- Expose explicit service operations when modules need to collaborate.
- Keep business-specific code within its owning module; use `shared/` for reusable technical code.

## Project Structure

The agreed structure is shown below. Maven Wrapper support files, frontend manifests, and the Spring Boot entry point must also be present in the working repository.

```text
GCMS/
├── backend/
│   ├── src/main/java/com/gcms/
│   │   ├── administration/
│   │   ├── booking/
│   │   ├── inventory/
│   │   ├── workforce/
│   │   ├── finance/
│   │   ├── shared/
│   │   └── config/
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   ├── application-dev.yml
│   │   ├── application-prod.yml
│   │   └── db/migration/
│   ├── src/test/
│   ├── .mvn/wrapper/
│   ├── pom.xml
│   ├── mvnw
│   └── mvnw.cmd
├── frontend/
│   ├── public/
│   ├── src/
│   │   ├── features/
│   │   │   ├── administration/
│   │   │   ├── booking/
│   │   │   ├── inventory/
│   │   │   ├── workforce/
│   │   │   └── finance/
│   │   ├── shared/
│   │   │   ├── components/
│   │   │   ├── hooks/
│   │   │   └── utils/
│   │   ├── layouts/
│   │   ├── routes/
│   │   ├── api/
│   │   ├── assets/
│   │   │   ├── images/
│   │   │   ├── icons/
│   │   │   └── styles/
│   │   ├── App.js
│   │   ├── App.css
│   │   ├── index.js
│   │   └── index.css
│   ├── package.json
│   └── package-lock.json
└── README.md
```

| Module | Ownership |
| --- | --- |
| `administration` | Accounts, access, branches, room configuration, pricing, packages, and audit history. |
| `booking` | Reservations, room sessions, deposits, checkout bills, and customer payments. |
| `inventory` | F&B catalog and order processing, stock, equipment, incidents, and repairs. |
| `workforce` | Staff, assignments, shifts, attendance, and payroll. |
| `finance` | Funds, expenses, reimbursements, cash reconciliation, and financial reporting. |

F&B charges are linked to an active booking and included in its checkout bill. Order processing and inventory updates belong to `inventory`; bill calculation belongs to `booking`. Accountant responsibilities span `booking`, `workforce`, and `finance`.

Within a backend feature, create `controller/`, `service/`, `repository/`, `entity/`, and `dto/` as needed. Within a frontend feature, use `pages/`, `components/`, `services/`, `hooks/`, and `styles/` as needed.

## Getting Started

### Prerequisites

- JDK 17; `JAVA_HOME` must point to the JDK.
- Node.js and npm compatible with the committed frontend dependencies.
- PostgreSQL running locally.
- Git.

Maven Wrapper is used, so a separate Maven installation is optional. Docker is optional for local development and is used for the planned container deployment.

### 1. Clone the repository

Replace `YOUR_REPOSITORY_URL` with the team's Git repository URL. The final argument sets the local folder name.

```bash
git clone YOUR_REPOSITORY_URL GCMS
cd GCMS
```

### 2. Create a development database

Run the following in pgAdmin Query Tool or `psql` using a PostgreSQL administrator account. Replace the example password before executing.

```sql
CREATE USER gcms_dev WITH PASSWORD 'REPLACE_WITH_LOCAL_PASSWORD';
CREATE DATABASE gcms OWNER gcms_dev;
```

PostgreSQL is separate from SQL Server. GCMS uses the PostgreSQL database and connection settings below.

### 3. Configure and run the backend

Ensure `application-dev.yml` uses the [configuration example](#configuration) below, or adjust the environment variables to match the project's existing configuration.

**Windows PowerShell — from the repository root:**

```powershell
cd backend
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:DB_URL = "jdbc:postgresql://localhost:5432/gcms"
$env:DB_USERNAME = "gcms_dev"
$env:DB_PASSWORD = "REPLACE_WITH_LOCAL_PASSWORD"
.\mvnw.cmd spring-boot:run
```

**macOS/Linux — from the repository root:**

```bash
cd backend
export SPRING_PROFILES_ACTIVE=dev
export DB_URL='jdbc:postgresql://localhost:5432/gcms'
export DB_USERNAME='gcms_dev'
export DB_PASSWORD='REPLACE_WITH_LOCAL_PASSWORD'
./mvnw spring-boot:run
```

The example configuration runs the backend at **http://localhost:8080**. Keep this terminal open.

### 4. Configure and run the frontend

Create `frontend/.env.local`:

```dotenv
REACT_APP_API_BASE_URL=http://localhost:8080/api/v1
```

`/api/v1` is the proposed API base path. Match it to the backend routes, and ensure the shared API client in `src/api/` reads `process.env.REACT_APP_API_BASE_URL`.

Open a second terminal at the repository root.

**Windows PowerShell:**

```powershell
cd frontend
npm.cmd ci
npm.cmd start
```

**macOS/Linux:**

```bash
cd frontend
npm ci
npm start
```

`npm ci` requires a committed `package-lock.json` matching `package.json`. If the project has no lockfile yet, run `npm install` once and commit the generated lockfile.

Open **http://localhost:3000**. Create React App uses `npm start`. Restart the frontend after changing `.env.local`.

### Troubleshooting

| Problem | Check or action |
| --- | --- |
| PowerShell blocks `npm.ps1` | Use `npm.cmd` as shown above. |
| Backend cannot connect to PostgreSQL | Check the database service, port, database name, username, and password. |
| Backend reports a missing table | Confirm the database migrations have been configured and applied. |
| Frontend cannot call the API | Check the backend is running, the API base path is correct, and backend CORS allows `http://localhost:3000`. |
| Java version mismatch | Check `java -version`, `JAVA_HOME`, and the IDE project SDK; use JDK 17. |

## Configuration

| File | Purpose |
| --- | --- |
| `application.yml` | Common backend settings. |
| `application-dev.yml` | Local development settings. |
| `application-prod.yml` | Production settings using environment-supplied secrets. |
| `frontend/.env.local` | Local frontend configuration; exclude from Git. |

Example `backend/src/main/resources/application-dev.yml`:

```yaml
server:
  port: 8080

spring:
  datasource:
    url: ${DB_URL:jdbc:postgresql://localhost:5432/gcms}
    username: ${DB_USERNAME:gcms_dev}
    password: ${DB_PASSWORD}
```

Set the active profile through `SPRING_PROFILES_ACTIVE`. Backend `.env` files are not loaded automatically by Spring Boot; supply variables through the terminal, IDE run configuration, or deployment environment.

Keep database passwords and SePay, email, and cloud-storage credentials outside source control. Frontend `REACT_APP_*` variables are exposed in the browser bundle and must contain only public configuration.

### Database migrations

Store schema changes under `backend/src/main/resources/db/migration/`. For Flyway, use names such as `V1__create_initial_schema.sql` and `V2__add_booking_indexes.sql`.

The directory alone does not run migrations. If the team selects Flyway, configure its compatible dependencies in `pom.xml`, including PostgreSQL support where required. With Flyway enabled, migrations can run at backend startup. Keep schema generation under migration control; use Hibernate `ddl-auto: validate` when the migrated schema is ready.

Add a new migration for changes to an already-shared schema. Preserve applied migrations and test changes against a development database.

## Usage

Once the relevant features and development accounts are available:

1. Sign in with a role-appropriate account and work within the permitted branch scope.
2. Configure branches, room categories, prices, rooms, and staff access.
3. Create a walk-in or advance booking after checking availability; record a deposit where applicable.
4. Check the customer in and manage room changes, extensions, and F&B orders during the session.
5. Review room and F&B charges at checkout, apply eligible adjustments, deduct the deposit, and record payment.
6. Complete daily cash closing and reconciliation, attendance closing, payroll, and management reporting as authorized.

Customers scan the in-room QR code to view the F&B menu and submit an order for their active room session.

Obtain development accounts through the team's agreed seed or account-provisioning process. No demo credentials are specified here.

## Testing

Run commands from the corresponding project directory.

| Check | Windows PowerShell | macOS/Linux |
| --- | --- | --- |
| Backend tests (`backend/`) | `.\mvnw.cmd test` | `./mvnw test` |
| Backend verification (`backend/`) | `.\mvnw.cmd verify` | `./mvnw verify` |
| Frontend tests (`frontend/`) | `npm.cmd test -- --watchAll=false` | `npm test -- --watchAll=false` |
| Frontend production build (`frontend/`) | `npm.cmd run build` | `npm run build` |

These commands assume the standard Maven and Create React App scripts are present. Database-dependent tests must use a separate test database and the configuration defined by the test suite.

Prioritize tests for booking conflicts, role and branch access, bill/deposit calculations, stock movements, payroll calculations, and financial reconciliation. API/system tests and UAT should cover complete workflows and their failure cases. Do not use production customer or financial data for testing.

## Contributing

Work on a separate branch and submit a pull request to the team's integration branch. Agree on the integration branch name before adopting a `develop` workflow.

Use descriptive branch names such as `feature/booking-create` or `fix/booking-conflict`. Use concise commit messages such as `feat: add booking creation`, `fix: validate overlapping bookings`, or `docs: update local setup`.

Before opening a pull request:

- Synchronize with the integration branch and resolve conflicts.
- Run checks relevant to the change and report the results.
- Describe the resulting behavior and any required configuration or migration.
- Update documentation when setup, APIs, or behavior changes.
- Check that no secrets or local generated files are included.

Review and test changes before merging. Keep `node_modules/`, `build/`, `target/`, and local environment files out of Git; preserve examples such as `.env.example` and Maven Wrapper files.

## Documentation

The detailed project references are:

- Report 1 — Project Introduction.
- Report 2 — Project Management Plan.
- GenZ Use Case List.
- GenZ Cinema AS-IS / TO-BE workflows.

Keep detailed requirements, design, business rules, and test documentation outside this README. Add repository-relative links when approved documents are placed in `docs/`, or link to their authorized shared location.

README structure reference: [How to Write a Good README File — freeCodeCamp](https://www.freecodecamp.org/news/how-to-write-a-good-readme-file/).

Technical setup references: [Create React App environment variables](https://create-react-app.dev/docs/adding-custom-environment-variables/), [Spring Boot profiles](https://docs.spring.io/spring-boot/reference/features/profiles.html), and [Flyway documentation](https://documentation.red-gate.com/flyway).

## Team

| Name | Project role |
| --- | --- |
| Nguyen Minh Cao | Team Leader |
| Dao Van Huy | Member |
| Cu Thi Huyen Trang | Member |
| Nguyen Van Trong | Member; business stakeholder as Branch Manager |
| Nguyen Tuan Thanh | Member |

**Supervisor:** Nguyen Thi Hanh.

## License

GCMS is developed as an FPT University capstone project. A software license has not been specified in the supplied project materials. The team should agree on usage and distribution terms and add a `LICENSE` file before granting reuse rights.
