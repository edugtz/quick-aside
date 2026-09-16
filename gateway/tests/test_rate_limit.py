import pytest

from quickaside_gateway.rate_limit import SlidingWindowRateLimiter


def test_rejects_invalid_configuration():
    with pytest.raises(ValueError):
        SlidingWindowRateLimiter(
            limit=0,
            window_seconds=60,
        )

    with pytest.raises(ValueError):
        SlidingWindowRateLimiter(
            limit=1,
            window_seconds=0,
        )

    with pytest.raises(ValueError):
        SlidingWindowRateLimiter(
            limit=1,
            window_seconds=60,
            max_keys=0,
        )


def test_enforces_sliding_window():
    limiter = SlidingWindowRateLimiter(
        limit=2,
        window_seconds=60,
    )

    assert limiter.allow("client", now=0)
    assert limiter.allow("client", now=1)
    assert not limiter.allow("client", now=2)

    assert limiter.allow("client", now=61)


def test_key_cardinality_is_bounded():
    limiter = SlidingWindowRateLimiter(
        limit=10,
        window_seconds=60,
        max_keys=128,
    )

    for index in range(5000):
        assert limiter.allow(
            f"client-{index}",
            now=0,
        )

    assert limiter.tracked_key_count == 128


def test_expired_high_cardinality_state_is_pruned():
    limiter = SlidingWindowRateLimiter(
        limit=10,
        window_seconds=60,
        max_keys=128,
    )

    for index in range(5000):
        assert limiter.allow(
            f"client-{index}",
            now=0,
        )

    assert limiter.tracked_key_count == 128

    assert limiter.allow(
        "fresh-client",
        now=61,
    )

    assert limiter.tracked_key_count == 1


def test_denied_access_refreshes_lru_position():
    limiter = SlidingWindowRateLimiter(
        limit=1,
        window_seconds=60,
        max_keys=2,
    )

    assert limiter.allow("a", now=0)
    assert limiter.allow("b", now=0)

    # "a" is active even though this request is rejected.
    assert not limiter.allow("a", now=1)

    # Adding "c" evicts the least-recently-used key, "b".
    assert limiter.allow("c", now=2)

    # "b" was evicted, so it gets a fresh bucket.
    assert limiter.allow("b", now=3)

    assert limiter.tracked_key_count == 2



def test_global_budget_bounds_high_cardinality_churn():
    per_source = SlidingWindowRateLimiter(
        limit=300,
        window_seconds=60,
        max_keys=8,
    )

    global_budget = SlidingWindowRateLimiter(
        limit=10,
        window_seconds=60,
        max_keys=1,
    )

    admitted = 0

    for index in range(100):
        source_allowed = per_source.allow(
            f"client-{index}",
            now=0,
        )

        if source_allowed and global_budget.allow(
            "global",
            now=0,
        ):
            admitted += 1

    assert per_source.tracked_key_count == 8
    assert global_budget.tracked_key_count == 1
    assert admitted == 10
