# Webshop Gateway

## Overview

Webshop Gateway is a Spring Boot application that acts as an entry point to the webshop backend services.

It forwards requests between the frontend and the backend microservices, including Auth Service, Product Service, and Order Service.

The gateway also handles login responses and manages the authentication cookie used by the frontend.

## Responsibilities

The Gateway handles:

* Forwarding authentication requests to Auth Service.
* Managing the `access_token` authentication cookie.
* Forwarding product requests to Product Service.
* Forwarding order creation requests to Order Service.
* Forwarding bearer tokens to backend services when available.
* Returning responses from backend services to the frontend.
* Providing OpenAPI documentation for the API.

## Technologies

* Java 21
* Spring Boot 4.1.1
* Spring Cloud 2025.1.3
* Spring Cloud Gateway Server Web MVC
* Spring Web MVC
* Spring Security
* OAuth2 Resource Server
* Spring REST Client
* Jackson
* Springdoc OpenAPI 3.1.1
* Maven
* JUnit 5

## Port

The default port is `9003`.

The port can be changed using the `PORT` environment variable.

## Environment Variables

Configure the following environment variables when necessary:

| Variable              | Description                                               | Default                 |
| --------------------- | --------------------------------------------------------- | ----------------------- |
| `PORT`                | Port used by the Gateway                                  | `9003`                  |
| `AUTH_SERVER_URL`     | Base URL of Auth Service                                  | `http://localhost:8080` |
| `PRODUCT_SERVICE_URL` | Base URL of Product Service                               | `http://localhost:8081` |
| `ORDER_SERVICE_URL`   | Base URL of Order Service                                 | `http://localhost:9000` |
| `COOKIE_SECURE`       | Enables the Secure attribute on the authentication cookie | `false`                 |
| `COOKIE_SAME_SITE`    | SameSite attribute for the authentication cookie          | `Lax`                   |

The default URLs assume that the corresponding services are running locally on their configured ports.

For deployments using HTTPS, configure the cookie settings according to the deployment environment.

## API Endpoints

All controller endpoints use the `/gateway` base path.

### Authentication

| Method | Endpoint                 | Description                                                                    |
| ------ | ------------------------ | ------------------------------------------------------------------------------ |
| `POST` | `/gateway/auth/login`    | Forwards login credentials to Auth Service and sets the authentication cookie. |
| `POST` | `/gateway/auth/register` | Forwards registration requests to Auth Service.                                |
| `POST` | `/gateway/auth/logout`   | Clears the authentication cookie.                                              |
| `GET`  | `/gateway/auth/jwks`     | Retrieves JWT key information from Auth Service.                               |
| `GET`  | `/gateway/auth/me`       | Retrieves information about the current user.                                  |

### Products

| Method   | Endpoint                        | Description                                             |
| -------- | ------------------------------- | ------------------------------------------------------- |
| `GET`    | `/gateway/products`             | Retrieves all products.                                 |
| `GET`    | `/gateway/products/{id}`        | Retrieves a product by ID.                              |
| `POST`   | `/gateway/products/add`         | Forwards a product creation request to Product Service. |
| `DELETE` | `/gateway/products/delete/{id}` | Forwards a product deletion request to Product Service. |

### Orders

| Method | Endpoint         | Description                                          |
| ------ | ---------------- | ---------------------------------------------------- |
| `POST` | `/gateway/order` | Forwards an order creation request to Order Service. |

## Authentication and Cookies

The Gateway receives login credentials and forwards them to Auth Service.

After a successful login, the Gateway reads the token and expiration time from the Auth Service response. It then sets an `access_token` cookie with the following attributes:

* `HttpOnly`
* `Secure`, configurable through `COOKIE_SECURE`
* `SameSite`, configurable through `COOKIE_SAME_SITE`
* `Path=/`
* Expiration based on the token's lifetime

The login response contains user information and roles without returning the access token in the response body.

When a request includes the `access_token` cookie, the Gateway can forward the token to backend services using the HTTP `Authorization: Bearer` header.

The logout endpoint clears the cookie.

### Security Configuration

The current Spring Security configuration disables CSRF protection, uses stateless session management, and permits all requests at the Gateway security-filter level.

Backend services may enforce their own authentication and authorization rules.

Review the security configuration before deploying the application to a production environment.

## Communication with Backend Services

The Gateway forwards requests to three backend services:

* **Auth Service** handles authentication and user-related requests.
* **Product Service** handles product-related requests.
* **Order Service** handles order creation.

The service URLs are configurable through environment variables.

## API Documentation

The project includes Springdoc OpenAPI.

When the application is running, the standard Springdoc endpoints are:

* Swagger UI: `/swagger-ui/index.html`
* OpenAPI specification: `/v3/api-docs`

The exact accessibility of these endpoints depends on the application's runtime configuration.

## Running Locally

### Prerequisites

* Java 21 or a compatible configured Java environment.
* The Maven Wrapper included in the repository.
* Auth Service running on its configured address.
* Product Service running on its configured address.
* Order Service running on its configured address when testing order creation.

### Windows

Open a terminal in the project root and run:

```powershell
mvnw.cmd spring-boot:run
```

### Linux/macOS

Open a terminal in the project root and run:

```bash
./mvnw spring-boot:run
```

The application starts on port `9003` by default.

## Related Repositories

* [Auth Service](https://github.com/conniponni/Auth-server)
* [Product Service](https://github.com/philipwmitilinos-max/Product_Service)
* [Order Service](https://github.com/sedina-imamovic/order-service-webshop)
* [Frontend](https://github.com/Stefanvill/agil-frontend)
* [Email Service](https://github.com/Stefanvill/email-service)
