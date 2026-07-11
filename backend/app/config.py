from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "BluePath API"
    environment: str = "development"
    database_url: str = "sqlite:///./bluepath.db"
    jwt_secret: str = "change-me-before-production"
    jwt_algorithm: str = "HS256"
    access_token_minutes: int = 60 * 24 * 7
    cors_origins: str = "*"

    llm_base_url: str = ""
    llm_api_key: str = ""
    llm_model: str = ""
    embedding_model: str = ""
    embedding_dimensions: int = 1536

    youtube_api_key: str = ""
    youtube_sync_hours: int = 0
    youtube_sync_queries: str = "해양 교육,해양 환경 교육,스마트 항만,자율운항선박"
    youtube_sync_max_results: int = 10
    admin_email: str = ""
    admin_password: str = ""

    password_reset_base_url: str = "http://localhost:8000/reset-password"
    password_reset_token_minutes: int = 30
    smtp_host: str = ""
    smtp_port: int = 587
    smtp_username: str = ""
    smtp_password: str = ""
    smtp_from_email: str = ""
    smtp_use_tls: bool = True

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    def validate_runtime(self) -> None:
        if self.environment.lower() not in {"production", "prod"}:
            return
        problems: list[str] = []
        if len(self.jwt_secret) < 32 or self.jwt_secret == "change-me-before-production":
            problems.append("JWT_SECRET must be a unique value of at least 32 characters")
        if self.cors_origins.strip() == "*":
            problems.append("CORS_ORIGINS must list trusted production origins")
        if self.admin_email and len(self.admin_password) < 12:
            problems.append("ADMIN_PASSWORD must contain at least 12 characters")
        if self.llm_base_url and not self.llm_model:
            problems.append("LLM_MODEL is required when LLM_BASE_URL is configured")
        if not self.password_reset_base_url.startswith("https://"):
            problems.append("PASSWORD_RESET_BASE_URL must use HTTPS in production")
        if self.smtp_host and not self.smtp_from_email:
            problems.append("SMTP_FROM_EMAIL is required when SMTP_HOST is configured")
        if problems:
            raise RuntimeError("Unsafe production configuration: " + "; ".join(problems))

    @property
    def cors_origin_list(self) -> list[str]:
        if self.cors_origins.strip() == "*":
            return ["*"]
        return [value.strip() for value in self.cors_origins.split(",") if value.strip()]

    @property
    def llm_enabled(self) -> bool:
        return bool(self.llm_base_url.strip() and self.llm_model.strip())

    @property
    def youtube_query_list(self) -> list[str]:
        return [value.strip() for value in self.youtube_sync_queries.split(",") if value.strip()]


@lru_cache
def get_settings() -> Settings:
    return Settings()
