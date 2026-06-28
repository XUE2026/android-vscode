#!/bin/bash
# Android VSCode - Compiler Installation Script
# Installs compilers for Python, Java, C++, Kotlin

set -e

LANGUAGE=$1

install_python() {
    echo "Installing Python..."
    proot-distro login debian -- bash -c '
    apt-get install -y python3 python3-pip python3-venv
    pip3 install --upgrade pip pylint autopep8 black
    '
    echo "Python installed successfully!"
}

install_java() {
    echo "Installing Java..."
    proot-distro login debian -- bash -c '
    apt-get install -y openjdk-17-jdk-headless gradle maven
    '
    echo "Java installed successfully!"
}

install_cpp() {
    echo "Installing C++..."
    proot-distro login debian -- bash -c '
    apt-get install -y build-essential gcc g++ clang make cmake gdb
    '
    echo "C++ installed successfully!"
}

install_kotlin() {
    echo "Installing Kotlin..."
    proot-distro login debian -- bash -c '
    apt-get install -y kotlin openjdk-17-jdk-headless gradle
    '
    echo "Kotlin installed successfully!"
}

install_all() {
    install_python
    install_java
    install_cpp
    install_kotlin
}

case "$LANGUAGE" in
    python) install_python ;;
    java) install_java ;;
    cpp|c++) install_cpp ;;
    kotlin) install_kotlin ;;
    all) install_all ;;
    *)
        echo "Usage: $0 {python|java|cpp|kotlin|all}"
        exit 1
        ;;
esac