@echo off
title landonorris.com local mirror - close this window to stop the server
cd /d "%~dp0"
start "" http://localhost:8735/
echo Serving at http://localhost:8735/  (close this window to stop)
python serve.py
