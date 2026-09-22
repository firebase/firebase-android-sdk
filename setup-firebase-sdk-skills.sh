# Pull the repo if already present, otherwise clone freshly
git -C .firebase-sdk-skills pull || git clone git@depot.code.corp.goog:experimental/firebase-sdk-team-skills.git .firebase-sdk-skills

# Copy newly cloned skills
mkdir -p .agents/skills
cp -r .firebase-sdk-skills/skills/android/* .agents/skills/
