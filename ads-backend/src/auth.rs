use axum::{
    extract::Request,
    http::{HeaderMap, StatusCode},
    middleware::Next,
    response::Response,
};
use std::env;

pub async fn require_api_key(
    headers: HeaderMap,
    request: Request,
    next: Next,
) -> Result<Response, StatusCode> {
    let expected_key = env::var("API_KEY").expect("API_KEY environment variable is missing!");

    if let Some(key) = headers.get("x-api-key")
        && key == expected_key.as_str()
    {
        return Ok(next.run(request).await);
    }

    tracing::warn!("Unauthorized access attempt! Invalid or missing x-api-key.");
    Err(StatusCode::UNAUTHORIZED)
}
