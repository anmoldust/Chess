# Taste

- Writes strict TypeScript and forbids `any` in the frontend. Confidence: 0.85
- Requires tests to be written and passing before committing code. Confidence: 0.8
- Models backend DTOs as Java records (the Spring Boot API surface is record-based). Confidence: 0.8
- For "production grade", wants a genuine microservice split — independent services behind an API gateway with a database per service — rather than a modular monolith. Confidence: 0.7
