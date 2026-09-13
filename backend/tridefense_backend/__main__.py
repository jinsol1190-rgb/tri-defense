import argparse
from wsgiref.simple_server import make_server
from .api import create_app
from .registry import RegistryClient
from .service import SubmissionService


def main():
    parser = argparse.ArgumentParser(description="Tri-Defense Backend P0: local transactions / MOCK_PROOF")
    parser.add_argument("--rpc-url", required=True)
    parser.add_argument("--registry", required=True)
    parser.add_argument("--sender", required=True)
    parser.add_argument("--deployment-block", type=int, required=True)
    parser.add_argument("--abi-dir", default="contracts/abi")
    parser.add_argument("--database", default="backend/submissions.sqlite3")
    parser.add_argument("--port", type=int, default=8000)
    args = parser.parse_args()
    registry = RegistryClient(args.rpc_url, args.registry, args.sender, args.abi_dir, args.deployment_block)
    service = SubmissionService(registry, args.database)
    try:
        with make_server("127.0.0.1", args.port, create_app(service)) as server:
            print("Backend P0 at http://127.0.0.1:%d — LOCAL_TRANSACTION / MOCK_PROOF" % args.port, flush=True)
            server.serve_forever()
    finally:
        service.close()


if __name__ == "__main__":
    main()
