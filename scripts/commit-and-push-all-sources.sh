#!/bin/sh
if [ "$#" -ne 1 ]; then
    echo "Usage: $0 <commit-message>"
    exit 1
fi

for dir in */ ; do
    if [ -d "$dir/.git" ]; then
        echo "Processing $dir"
        (
            cd "$dir" || exit
            git add .
            git commit -m "$1"
            git push
        )
    fi
done

echo "Done!"
