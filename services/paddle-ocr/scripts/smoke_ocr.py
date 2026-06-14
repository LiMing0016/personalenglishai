import argparse
import base64
import json
import urllib.request
from pathlib import Path


def post_json(url: str, payload: dict) -> dict:
    data = json.dumps(payload).encode("utf-8")
    request = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.loads(response.read().decode("utf-8"))


def main() -> None:
    parser = argparse.ArgumentParser(description="Smoke test for the PaddleOCR HTTP service.")
    parser.add_argument("--base-url", default="http://127.0.0.1:8090")
    parser.add_argument("--pdf", type=Path)
    parser.add_argument("--image", type=Path)
    parser.add_argument("--max-pages", type=int, default=2)
    args = parser.parse_args()

    if not args.pdf and not args.image:
        raise SystemExit("Provide --pdf or --image")

    if args.pdf:
        payload = {
            "documentBase64": base64.b64encode(args.pdf.read_bytes()).decode("ascii"),
            "language": "ch,eng",
            "maxPages": args.max_pages,
            "enableTextOcr": True,
            "enableFormula": False,
        }
        result = post_json(f"{args.base_url.rstrip('/')}/ocr/pdf", payload)
    else:
        payload = {
            "imageBase64": base64.b64encode(args.image.read_bytes()).decode("ascii"),
            "language": "ch,eng",
            "enableTextOcr": True,
            "enableFormula": False,
        }
        result = post_json(f"{args.base_url.rstrip('/')}/ocr/image", payload)

    print(json.dumps({
        "status": result.get("status"),
        "pageCount": result.get("pageCount"),
        "recognizedPageCount": result.get("recognizedPageCount"),
        "warnings": result.get("warnings"),
        "elapsedMs": result.get("elapsedMs"),
    }, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
