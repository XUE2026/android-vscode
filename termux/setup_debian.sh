#!/bin/bash
# Android VSCode - Termux proot-distro Debian Setup Script
# This script sets up a Debian environment in Termux for compiling code

set -e

echo "=== Android VSCode - Termux Debian Setup ==="
echo ""

# Update Termux packages
echo "[1/6] Updating Termux packages..."
pkg update -y && pkg upgrade -y

# Install proot-distro
echo "[2/6] Installing proot-distro..."
pkg install -y proot-distro

# Install Debian
echo "[3/6] Installing Debian (this may take a while)..."
proot-distro install debian

# Login to Debian and set up basic tools
echo "[4/6] Setting up Debian environment..."
proot-distro login debian -- bash -c '
apt-get update -y && apt-get upgrade -y
apt-get install -y sudo curl wget git vim nano openssh
'

# Install compilers and build tools in Debian
echo "[5/6] Installing compilers and build tools..."
proot-distro login debian -- bash -c '
apt-get install -y \
    build-essential \
    gcc g++ \
    clang \
    make cmake \
    python3 python3-pip python3-venv \
    openjdk-17-jdk-headless \
    kotlin \
    nodejs npm \
    ruby \
    php \
    golang-go \
    rustc cargo \
    mono-complete \
    default-jdk \
    gradle \
    maven \
    git \
    unzip zip \
    curl wget

# Set up Python tools
pip3 install --upgrade pip
pip3 install pylint autopep8 black mypy

# Set up Node.js tools
npm install -g typescript ts-node eslint prettier
'

echo "[6/6] Setup complete!"
echo ""
echo "=== Debian environment is ready ==="
echo "To access Debian: proot-distro login debian"
echo "To run commands in Debian: proot-distro login debian -- bash -c 'your command'"
echo ""
echo "You can now use Android VSCode with Termux Debian!"