#!/bin/bash
input_file_directory=$*
find "$input_file_directory"  -name '*.csv' -exec cat {} \;
