from backend.supabase_store import SupabaseStore


def test_supabase_store_requires_both_server_credentials(monkeypatch):
    monkeypatch.setenv("SUPABASE_URL", "https://example.supabase.co")
    monkeypatch.delenv("SUPABASE_SECRET_KEY", raising=False)
    store = SupabaseStore()
    assert store.enabled is False


def test_supabase_store_uses_private_bucket_defaults(monkeypatch):
    monkeypatch.setenv("SUPABASE_URL", "https://example.supabase.co")
    monkeypatch.setenv("SUPABASE_SECRET_KEY", "server-only-test-secret")
    store = SupabaseStore()
    assert store.enabled is True
    assert store.source_bucket == "clipvlip-sources"
    assert store.output_bucket == "clipvlip-outputs"
    assert store._headers()["Authorization"] == "Bearer server-only-test-secret"
    assert store._headers()["apikey"] == "server-only-test-secret"
