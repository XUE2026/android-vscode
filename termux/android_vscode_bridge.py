#!/usr/bin/env python3
"""
Android VSCode - Termux Bridge Script
This script runs in Termux proot-distro Debian and provides a bridge
between Android VSCode and the compiler environment.
It listens on a local socket/HTTP for commands from Android VSCode.

Usage: python3 android_vscode_bridge.py [--port PORT] [--host HOST]
"""

import os
import sys
import json
import subprocess
import tempfile
import shutil
import http.server
import socketserver
import urllib.parse
import threading
import argparse
from pathlib import Path

# Configuration
DEFAULT_PORT = 9876
DEFAULT_HOST = "127.0.0.1"
WORKSPACE_DIR = os.path.expanduser("~/android_vscode_workspace")
os.makedirs(WORKSPACE_DIR, exist_ok=True)

class CompilerManager:
    """Manages compilers and build tools"""
    
    def __init__(self):
        self.compilers = self._detect_compilers()
    
    def _detect_compilers(self):
        compilers = {}
        checks = {
            "python3": ["python3", "--version"],
            "python": ["python", "--version"],
            "gcc": ["gcc", "--version"],
            "g++": ["g++", "--version"],
            "clang": ["clang", "--version"],
            "clang++": ["clang++", "--version"],
            "make": ["make", "--version"],
            "cmake": ["cmake", "--version"],
            "javac": ["javac", "--version"],
            "java": ["java", "--version"],
            "kotlinc": ["kotlinc", "-version"],
            "node": ["node", "--version"],
            "npm": ["npm", "--version"],
            "go": ["go", "version"],
            "rustc": ["rustc", "--version"],
            "cargo": ["cargo", "--version"],
            "gradle": ["gradle", "--version"],
            "maven": ["mvn", "--version"],
            "git": ["git", "--version"],
            "php": ["php", "--version"],
            "ruby": ["ruby", "--version"],
        }
        for name, cmd in checks.items():
            try:
                result = subprocess.run(cmd, capture_output=True, text=True, timeout=10)
                compilers[name] = {
                    "available": result.returncode == 0,
                    "version": result.stdout.strip().split('\n')[0] if result.stdout else "",
                    "path": shutil.which(cmd[0]) or ""
                }
            except Exception:
                compilers[name] = {"available": False, "version": "", "path": ""}
        return compilers
    
    def get_compilers(self):
        return self.compilers
    
    def compile_code(self, language, source_path, output_path=None, args=None):
        """Compile source code for the given language"""
        results = {
            "success": False,
            "output": "",
            "error": "",
            "exit_code": -1,
            "language": language
        }
        
        try:
            if language == "python":
                result = subprocess.run(
                    ["python3", "-m", "py_compile", source_path],
                    capture_output=True, text=True, timeout=30
                )
            elif language == "java":
                output_dir = output_path or os.path.dirname(source_path)
                result = subprocess.run(
                    ["javac", "-d", output_dir, source_path],
                    capture_output=True, text=True, timeout=30
                )
            elif language in ("cpp", "c++", "c"):
                output = output_path or os.path.splitext(source_path)[0]
                compiler = "g++" if language in ("cpp", "c++") else "gcc"
                result = subprocess.run(
                    [compiler, "-std=c++17", "-o", output, source_path],
                    capture_output=True, text=True, timeout=30
                )
            elif language == "kotlin":
                result = subprocess.run(
                    ["kotlinc", source_path, "-include-runtime", "-d", 
                     output_path or os.path.splitext(source_path)[0] + ".jar"],
                    capture_output=True, text=True, timeout=30
                )
            else:
                results["error"] = f"Unsupported language: {language}"
                return results
            
            results["success"] = result.returncode == 0
            results["output"] = result.stdout
            results["error"] = result.stderr
            results["exit_code"] = result.returncode
        except subprocess.TimeoutExpired:
            results["error"] = "Compilation timed out"
        except Exception as e:
            results["error"] = str(e)
        
        return results
    
    def run_code(self, language, source_path, args=None):
        """Run compiled or interpreted code"""
        results = {
            "success": False,
            "output": "",
            "error": "",
            "exit_code": -1
        }
        
        try:
            if language == "python":
                cmd = ["python3", source_path]
            elif language == "java":
                class_name = os.path.splitext(os.path.basename(source_path))[0]
                cmd = ["java", "-cp", os.path.dirname(source_path), class_name]
            elif language in ("cpp", "c++", "c"):
                exe_path = os.path.splitext(source_path)[0]
                cmd = [exe_path]
            elif language == "kotlin":
                jar_path = os.path.splitext(source_path)[0] + ".jar"
                cmd = ["java", "-jar", jar_path]
            else:
                results["error"] = f"Unsupported language: {language}"
                return results
            
            if args:
                cmd.extend(args)
            
            result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
            results["success"] = result.returncode == 0
            results["output"] = result.stdout
            results["error"] = result.stderr
            results["exit_code"] = result.returncode
        except subprocess.TimeoutExpired:
            results["error"] = "Execution timed out"
        except Exception as e:
            results["error"] = str(e)
        
        return results
    
    def format_code(self, language, source_path):
        """Format source code"""
        results = {"success": False, "output": "", "error": ""}
        try:
            if language == "python":
                result = subprocess.run(
                    ["autopep8", "--in-place", source_path],
                    capture_output=True, text=True, timeout=30
                )
            elif language == "java":
                result = subprocess.run(
                    ["clang-format", "-i", source_path],
                    capture_output=True, text=True, timeout=30
                )
            elif language in ("cpp", "c++", "c"):
                result = subprocess.run(
                    ["clang-format", "-i", source_path],
                    capture_output=True, text=True, timeout=30
                )
            else:
                results["error"] = f"Formatting not supported for {language}"
                return results
            results["success"] = result.returncode == 0
            results["output"] = result.stdout
            results["error"] = result.stderr
        except Exception as e:
            results["error"] = str(e)
        return results
    
    def lint_code(self, language, source_path):
        """Lint source code"""
        results = {"success": False, "output": "", "error": "", "issues": []}
        try:
            if language == "python":
                result = subprocess.run(
                    ["pylint", "--output-format=json", source_path],
                    capture_output=True, text=True, timeout=30
                )
                try:
                    results["issues"] = json.loads(result.stdout)
                except:
                    results["issues"] = []
                results["output"] = result.stdout
                results["error"] = result.stderr
            else:
                results["error"] = f"Linting not supported for {language}"
                return results
            results["success"] = True
        except Exception as e:
            results["error"] = str(e)
        return results


class BridgeHTTPHandler(http.server.BaseHTTPRequestHandler):
    """HTTP request handler for the bridge server"""
    
    compiler_manager = CompilerManager()
    
    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        params = urllib.parse.parse_qs(parsed.query)
        
        if path == "/api/status":
            self._send_json({"status": "running", "version": "1.0.0"})
        elif path == "/api/compilers":
            self._send_json(self.compiler_manager.get_compilers())
        elif path == "/api/workspace":
            self._send_json({"workspace": WORKSPACE_DIR})
        else:
            self._send_json({"error": "Not found"}, 404)
    
    def do_POST(self):
        content_length = int(self.headers.get("Content-Length", 0))
        body = self.rfile.read(content_length) if content_length > 0 else b"{}"
        try:
            data = json.loads(body)
        except:
            data = {}
        
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        
        if path == "/api/compile":
            result = self.compiler_manager.compile_code(
                data.get("language", ""),
                data.get("source_path", ""),
                data.get("output_path"),
                data.get("args")
            )
            self._send_json(result)
        elif path == "/api/run":
            result = self.compiler_manager.run_code(
                data.get("language", ""),
                data.get("source_path", ""),
                data.get("args")
            )
            self._send_json(result)
        elif path == "/api/format":
            result = self.compiler_manager.format_code(
                data.get("language", ""),
                data.get("source_path", "")
            )
            self._send_json(result)
        elif path == "/api/lint":
            result = self.compiler_manager.lint_code(
                data.get("language", ""),
                data.get("source_path", "")
            )
            self._send_json(result)
        elif path == "/api/execute":
            cmd = data.get("command", "")
            if cmd:
                result = subprocess.run(
                    cmd, shell=True, capture_output=True, text=True, timeout=60,
                    cwd=data.get("working_dir", WORKSPACE_DIR)
                )
                self._send_json({
                    "success": result.returncode == 0,
                    "output": result.stdout,
                    "error": result.stderr,
                    "exit_code": result.returncode
                })
            else:
                self._send_json({"error": "No command provided"}, 400)
        else:
            self._send_json({"error": "Not found"}, 404)
    
    def _send_json(self, data, status=200):
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False).encode("utf-8"))
    
    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
    
    def log_message(self, format, *args):
        pass  # Suppress logs


def main():
    parser = argparse.ArgumentParser(description="Android VSCode Bridge Server")
    parser.add_argument("--port", type=int, default=DEFAULT_PORT, help="Port to listen on")
    parser.add_argument("--host", type=str, default=DEFAULT_HOST, help="Host to bind to")
    args = parser.parse_args()
    
    print(f"Android VSCode Bridge Server starting on {args.host}:{args.port}")
    print(f"Workspace: {WORKSPACE_DIR}")
    print(f"Python version: {sys.version}")
    print("-" * 50)
    
    # Print available compilers
    cm = CompilerManager()
    compilers = cm.get_compilers()
    print("Available compilers:")
    for name, info in compilers.items():
        status = "AVAILABLE" if info["available"] else "NOT FOUND"
        print(f"  {name}: {status} {info.get('version', '')}")
    print("-" * 50)
    
    with socketserver.TCPServer((args.host, args.port), BridgeHTTPHandler) as httpd:
        print(f"Server listening on {args.host}:{args.port}")
        print("Press Ctrl+C to stop")
        try:
            httpd.serve_forever()
        except KeyboardInterrupt:
            print("\nShutting down...")
            httpd.shutdown()


if __name__ == "__main__":
    main()