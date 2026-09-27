#!/bin/bash
# The verdict of a cycle (tools/Cycle.java) in the summary of the run and as an annotation of the job : an error for a
# blocking job, a warning for a non-blocking one (annotations are readable through the public API without logging in,
# unlike the logs). Run from the workspace (showcase/, cycle.log, install.log) with LABEL, RUNNER and BLOCKING set.
# Portable : bash 3.2 and the BSD tools of macOS, Git Bash on Windows, GNU tools on Linux. Exits 0 : the cycle step
# fails the job.
c=showcase/comparison
summary=$c/diff-$LABEL/summary.txt
verdict=$(grep -m1 -E '^(MATCH|MISMATCH) :' "$c/logs-$LABEL/compare.txt" 2>/dev/null) || verdict="no comparison"
# what failed : the end of the cycle, a build failure (with the error lines after it), or the install
failed="" details=""
if [ -f cycle.log ]; then
    failed=$(grep -m1 -E "cycle $LABEL FAILED|(JVM|native) build FAILED|native build : |Invalid label|Unknown option|usage: " \
        cycle.log | sed 's/^\[[0-9:]*\] //')
elif [ -f install.log ]; then
    failed="installing quarkus-desktop failed"
    details=$(grep -E '^\[ERROR\]' install.log | head -15)
fi
if [ -f "$summary" ]; then
    details=$({ grep -E '^(DIFFERENT|SIZE|ONLY_A|ONLY_B|ENV DIFF)' "$summary"
                sed -n '/^== Checks and errors/,$p' "$summary" | tail -n +2 | grep -v 'expected: '; } | head -30)
elif [ "${failed#*build FAILED}" != "$failed" ]; then
    details=$(sed -n -E '/(JVM|native) build FAILED/,$p' cycle.log | tail -n +2 | head -12)
fi
# what the runs got : the screen, the Java2D pipeline, and on macOS the privacy permissions (-Dshowcase.robot=true)
environment=""
k='screen|pipeline|headless|macos\.tcc\.screenCapture|macos\.tcc\.input'
for run in jvm jvm2 native; do
    report=$c/$run-$LABEL/report.json
    [ -f "$report" ] || continue
    keys=$(sed -n -E -e "s/^  \"($k)\": \"([^\"]*)\",?\$/\1=\2/p" -e "s/^  \"($k)\": ([^\",]*),?\$/\1=\2/p" "$report" \
        | paste -s -d ';' -)
    environment="$environment$run: $keys
"
done
suffix="" level=error
if [ "$BLOCKING" != true ]; then suffix=" (non-blocking)" level=warning; fi
if [ -n "$failed" ] || [ "${verdict#MATCH}" = "$verdict" ]; then
    # one line : the % and the line breaks encoded
    message=$(printf '%s\n%s\n' "${failed:-$verdict}" "$details" \
        | awk 'BEGIN { ORS = "" } { gsub(/%/, "%25"); gsub(/\r/, "%0D"); if (NR > 1) print "%0A"; print }')
    echo "::$level title=Showcase $LABEL on $RUNNER$suffix::$message"
fi
{
    echo "### $LABEL on $RUNNER$suffix"
    echo
    echo "\`$verdict\`"
    if [ -n "$failed" ]; then echo; echo "$failed"; fi
    if [ -n "$environment" ]; then echo; echo '```'; printf '%s' "$environment"; echo '```'; fi
    if [ -n "$details" ]; then echo; echo '```'; echo "$details"; echo '```'; fi
    if [ -f "$c/trace-$LABEL/metadata-diff.md" ]; then
        echo; echo "MetadataDiff (informational, see the artifact):"
        grep '^## ' "$c/trace-$LABEL/metadata-diff.md" | sed 's/^## /- /' || echo "- (no MetadataDiff output)"
    fi
} >> "$GITHUB_STEP_SUMMARY"
exit 0
