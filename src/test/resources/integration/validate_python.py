"""Helper script for Python syntax validation.
Usage: python validate_python.py <file>
Exit code 0 = valid, non-zero = syntax error.
"""
import sys
import py_compile
import tempfile
import os

def main():
    if len(sys.argv) < 2:
        print("Usage: python validate_python.py <file>", file=sys.stderr)
        sys.exit(2)

    file_path = sys.argv[1]
    if not os.path.isfile(file_path):
        print(f"File not found: {file_path}", file=sys.stderr)
        sys.exit(2)

    try:
        py_compile.compile(file_path, doraise=True)
        print("OK")
        sys.exit(0)
    except py_compile.PyCompileError as e:
        print(str(e), file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()
