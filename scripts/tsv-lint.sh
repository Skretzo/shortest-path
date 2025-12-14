#!/bin/bash

# TSV Linter: Checks that each line in TSV files has the same number of columns
# Usage: ./tsv-lint.sh [directory]

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEFAULT_DIR="$SCRIPT_DIR/../src/main/resources"
RESOURCE_DIR="${1:-$DEFAULT_DIR}"

exit_code=0

# Find all TSV files recursively
for file in $(find "$RESOURCE_DIR" -name "*.tsv" -type f | sort); do
    expected_columns=""
    header_line=""
    line_num=0
    errors_in_file=0

    while IFS= read -r line || [[ -n "$line" ]]; do
        line_num=$((line_num + 1))

        # Skip empty lines and comment lines
        if [[ -z "$line" || "$line" =~ ^[[:space:]]*# ]]; then
            continue
        fi

        # Count columns (number of tabs + 1)
        num_columns=$(($(printf '%s' "$line" | tr -cd '\t' | wc -c) + 1))

        if [[ -z "$expected_columns" ]]; then
            # First non-empty, non-comment line sets the expected column count
            expected_columns=$num_columns
            header_line=$line_num
        elif [[ "$num_columns" -ne "$expected_columns" ]]; then
            if [[ $errors_in_file -eq 0 ]]; then
                echo "File: $file"
            fi
            echo "  Line $line_num: expected $expected_columns columns, found $num_columns"
            errors_in_file=$((errors_in_file + 1))
            exit_code=1
        fi
    done < "$file"

    if [[ $errors_in_file -gt 0 ]]; then
        echo "  (Expected column count based on line $header_line)"
        echo ""
    fi
done

if [[ $exit_code -eq 0 ]]; then
    echo "All TSV files have consistent column counts."
fi

exit $exit_code
