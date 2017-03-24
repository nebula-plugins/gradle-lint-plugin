#!/bin/bash
# This script will build the project.

SWITCHES="-s --console=plain --info"

if [ $CIRCLE_PR_NUMBER ]; then
  echo -e "Build Pull Request #$CIRCLE_PR_NUMBER => Branch [$CIRCLE_BRANCH]"
  ./gradlew clean build $SWITCHES
elif [ -z $CIRCLE_TAG ]; then
  echo -e 'Build Branch with Snapshot => Branch ['$CIRCLE_BRANCH']'
  ./gradlew -Prelease.disableGitChecks=true snapshot $SWITCHES
elif [ $CIRCLE_TAG ]; then
  echo -e 'Build Branch for Release => Branch ['$CIRCLE_BRANCH']  Tag ['$CIRCLE_TAG']'
  case "$CIRCLE_TAG" in
  *-rc\.*)
    ./gradlew -Prelease.disableGitChecks=true -Prelease.useLastTag=true candidate $SWITCHES
    ;;
  *)
    ./gradlew -Prelease.disableGitChecks=true -Prelease.useLastTag=true final $SWITCHES
    ;;
  esac
else
  echo -e 'WARN: Should not be here => Branch ['$CIRCLE_BRANCH']  Tag ['$CIRCLE_TAG']  Pull Request ['$CIRCLE_PR_NUMBER']'
  ./gradlew build $SWITCHES
fi

EXIT=$?

exit $EXIT
