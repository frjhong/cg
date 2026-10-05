"""Run against an app started with AI_FIXTURE=src/test/resources/sample-plan.json."""
import json
from pathlib import Path
from playwright.sync_api import sync_playwright

ROOT = Path(__file__).resolve().parents[1]
with sync_playwright() as p:
    browser = p.chromium.launch(executable_path="/usr/bin/chromium", args=["--no-sandbox"])
    page = browser.new_page(viewport={"width": 1200, "height": 900})
    errors = []
    page.on("pageerror", lambda e: errors.append(str(e)))
    page.goto("http://127.0.0.1:8080")
    page.get_by_text("範例語意判讀重播", exact=True).wait_for()
    page.locator("#files").set_input_files([str(ROOT / "src/test/resources/samples/0916.docx"), str(ROOT / "src/test/resources/samples/0930.docx")])
    page.locator("#date0").fill("2026-09-16")
    with page.expect_response("**/api/merge") as response:
        page.locator("#merge").click()
    payload = response.value.json()
    assert response.value.status == 200, payload
    assert payload["summary"]["itemCount"] == 27
    page.locator("#result").wait_for(state="visible")
    assert page.locator("#decisions tr").count() == 27
    page.screenshot(path=str(ROOT / "artifacts/介面_桌面.png"), full_page=True)
    with page.expect_download() as download:
        page.locator("#download").click()
    download.value.save_as(str(ROOT / "artifacts/瀏覽器下載驗證.docx"))
    with page.expect_download() as audit:
        page.locator("#audit").click()
    audit.value.save_as(str(ROOT / "artifacts/瀏覽器比對紀錄.json"))
    assert json.loads((ROOT / "artifacts/瀏覽器比對紀錄.json").read_text())["oldOnlyItems"] == 14
    page.set_viewport_size({"width": 390, "height": 844})
    assert page.evaluate("document.documentElement.scrollWidth <= window.innerWidth")
    page.screenshot(path=str(ROOT / "artifacts/介面_手機.png"), full_page=True)
    page.locator("#remove").click()
    page.get_by_text("暫存結果已刪除。", exact=True).wait_for()
    assert page.request.get("http://127.0.0.1:8080" + payload["downloadUrl"]).status == 404
    assert not errors, errors
    (ROOT / "artifacts/browser-validation.json").write_text(json.dumps({"passed": True, "rows": 27, "download": True, "audit": True, "dateOverride": True, "mobileOverflow": False, "delete": True, "consoleErrors": errors}, ensure_ascii=False, indent=2))
    browser.close()
print("Browser flow passed: upload, date override, 27 rows, DOCX/audit downloads, mobile viewport, deletion")
