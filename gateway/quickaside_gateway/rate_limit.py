from __future__ import annotations

import threading
import time
from collections import OrderedDict, deque


class SlidingWindowRateLimiter:
    def __init__(
        self,
        limit: int,
        window_seconds: float,
        max_keys: int = 4096,
    ):
        if limit <= 0:
            raise ValueError("limit must be positive")

        if window_seconds <= 0:
            raise ValueError("window_seconds must be positive")

        if max_keys <= 0:
            raise ValueError("max_keys must be positive")

        self.limit = limit
        self.window_seconds = window_seconds
        self.max_keys = max_keys

        self._events: OrderedDict[str, deque[float]] = OrderedDict()
        self._lock = threading.Lock()

    def _prune_expired_keys(
        self,
        cutoff: float,
    ) -> None:
        expired_keys = [
            key
            for key, events in self._events.items()
            if not events or events[-1] <= cutoff
        ]

        for key in expired_keys:
            del self._events[key]

    @property
    def tracked_key_count(self) -> int:
        with self._lock:
            return len(self._events)

    def allow(
        self,
        key: str,
        *,
        now: float | None = None,
    ) -> bool:
        current = time.monotonic() if now is None else now
        cutoff = current - self.window_seconds

        with self._lock:
            events = self._events.get(key)

            if events is not None:
                while events and events[0] <= cutoff:
                    events.popleft()

                if not events:
                    del self._events[key]
                    events = None

            if events is None:
                if len(self._events) >= self.max_keys:
                    self._prune_expired_keys(cutoff)

                if len(self._events) >= self.max_keys:
                    self._events.popitem(last=False)

                events = deque()
                self._events[key] = events
            else:
                # Access counts as recent activity even if the request
                # is subsequently rejected by the rate limit.
                self._events.move_to_end(key)

            if len(events) >= self.limit:
                return False

            events.append(current)
            return True
