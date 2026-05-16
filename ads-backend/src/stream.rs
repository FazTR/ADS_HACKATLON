use axum::{
    extract::State,
    response::sse::{Event, Sse},
};
use futures::stream::Stream;
use std::convert::Infallible;
use tokio_stream::StreamExt;
use tokio_stream::wrappers::BroadcastStream;

pub async fn sse_handler(
    State(state): State<crate::AppState>,
) -> Sse<impl Stream<Item = Result<Event, Infallible>>> {
    let rx = state.tx.subscribe();

    // BroadcastStream wraps the rx into a Stream
    let stream = BroadcastStream::new(rx).filter_map(|result| match result {
        Ok(event) => {
            let json_data = serde_json::to_string(&event).unwrap_or_default();
            Some(Ok(Event::default()
                .event(&event.event_type)
                .data(json_data)))
        }
        Err(_) => None, // Ignore Lagged errors
    });

    Sse::new(stream).keep_alive(
        axum::response::sse::KeepAlive::new()
            .interval(std::time::Duration::from_secs(15))
            .text("keep-alive-text"),
    )
}
