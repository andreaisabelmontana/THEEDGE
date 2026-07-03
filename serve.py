"""Static server for the local landonorris.com study mirror.

Same as `python -m http.server` but sends Cache-Control: no-store so the
browser never serves stale copies of the patched files.
"""
import http.server
import functools

class NoStoreHandler(http.server.SimpleHTTPRequestHandler):
    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()

if __name__ == "__main__":
    import os
    os.chdir(os.path.dirname(os.path.abspath(__file__)))
    http.server.ThreadingHTTPServer(("", 8735), NoStoreHandler).serve_forever()
