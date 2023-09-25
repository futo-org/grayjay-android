#!/bin/sh
for dir in */ ; do
    if [ -d "$dir/.git" ]; then
        echo "Pulling latest changes for $dir"
        (
            cd "$dir" || exit
            git pull
        )
    fi
done

echo "Done!"
