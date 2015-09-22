#!/bin/bash
input_file_directory=$*
echo find "$input_file_directory"  \( -name '*.mp3' -or -name '*.ogg' -or -name '*.wav' -or -name '*.flac' \) -exec java -jar FrequencyDump.jar {} -s \;
find "$input_file_directory"  \( -name '*.mp3' -or -name '*.ogg' -or -name '*.wav' -or -name '*.flac' \) -exec java -jar FrequencyDump.jar {} -s \;
