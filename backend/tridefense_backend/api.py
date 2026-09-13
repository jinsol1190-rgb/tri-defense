"""Local-only WSGI API. No AI execution, verifier fixture admission or promotion endpoint."""
import json
import logging
from http import HTTPStatus
from urllib.parse import parse_qs
from .errors import ApiError
from .schema import hex_bytes

MAX_BODY_BYTES = 150000
LOG = logging.getLogger(__name__)


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ApiError(400, "DUPLICATE_JSON_FIELD")
        result[key] = value
    return result


def create_app(service):
    def app(environ, start_response):
        try:
            method, path = environ["REQUEST_METHOD"], environ["PATH_INFO"]
            query = parse_qs(environ.get("QUERY_STRING", ""), keep_blank_values=True)
            if method == "POST" and path == "/v1/threat-submissions":
                if query:
                    raise ApiError(400, "UNEXPECTED_QUERY")
                if environ.get("CONTENT_TYPE", "").split(";")[0].strip() != "application/json":
                    raise ApiError(415, "JSON_REQUIRED")
                try:
                    length = int(environ.get("CONTENT_LENGTH", "0"))
                except ValueError:
                    raise ApiError(400, "INVALID_CONTENT_LENGTH")
                if not 0 < length <= MAX_BODY_BYTES:
                    raise ApiError(413, "INVALID_BODY_SIZE")
                try:
                    body = json.loads(environ["wsgi.input"].read(length), object_pairs_hook=unique_object)
                except (ValueError, UnicodeError):
                    raise ApiError(400, "INVALID_JSON")
                response, code = service.submit(body), 202
            elif method == "GET" and path.startswith("/v1/threat-submissions/"):
                if query:
                    raise ApiError(400, "UNEXPECTED_QUERY")
                response, code = service.status(path[len("/v1/threat-submissions/"):]), 200
            elif method == "GET" and path.startswith("/v1/registry/threats/"):
                if query:
                    raise ApiError(400, "UNEXPECTED_QUERY")
                identifier = "0x" + hex_bytes(path[len("/v1/registry/threats/"):], 32).hex()
                service.registry.ensure_chain()
                response, code = {**service.registry.record(identifier), **service.registry.meta}, 200
            elif method == "GET" and path == "/v1/registry/events":
                if set(query) - {"cursor"} or len(query.get("cursor", [])) > 1:
                    raise ApiError(400, "INVALID_QUERY")
                service.registry.ensure_chain()
                response, code = service.registry.events(query.get("cursor", [None])[0]), 200
            else:
                raise ApiError(404, "ROUTE_NOT_FOUND")
        except ApiError as exc:
            code = exc.status
            response = {"errorCode": exc.code, **exc.details, **service.registry.meta}
        except Exception:
            # Never expose provider URLs, request proof, or raw internal exception details.
            LOG.warning("Backend dependency operation failed")
            code, response = 503, {"errorCode": "DEPENDENCY_UNAVAILABLE", **service.registry.meta}
        data = json.dumps(response, separators=(",", ":")).encode()
        start_response(str(code) + " " + HTTPStatus(code).phrase,
                       [("Content-Type", "application/json"), ("Content-Length", str(len(data))),
                        ("Cache-Control", "no-store")])
        return [data]
    return app
