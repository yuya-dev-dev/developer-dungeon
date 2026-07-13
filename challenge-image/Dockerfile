FROM alpine:3.23.3@sha256:59855d3dceb3ae53991193bd03301e082b2a7faa56a514b03527ae0ec2ce3a95

RUN apk add --no-cache git=2.52.0-r0 \
 && addgroup -S -g 10001 challenge \
 && adduser -S -D -H -u 10001 -G challenge challenge \
 && mkdir -p /opt/fixtures/stage-git-01 /opt/fixtures/stage-git-02 /opt/fixtures/stage-git-03 /opt/fixtures/stage-git-04 /opt/fixtures/stage-git-05 /opt/empty-hooks \
 && git init --initial-branch=main /opt/fixtures/stage-git-01 \
 && rm -rf /opt/fixtures/stage-git-01/.git/hooks \
 && mkdir -p /opt/fixtures/stage-git-01/.git/hooks \
 && printf 'release=1\n' > /opt/fixtures/stage-git-01/release.txt \
 && git -C /opt/fixtures/stage-git-01 add release.txt \
 && GIT_AUTHOR_DATE='2026-01-01T00:00:00Z' GIT_COMMITTER_DATE='2026-01-01T00:00:00Z' git -C /opt/fixtures/stage-git-01 -c user.name='Developer Dungeon' -c user.email='fixture@example.invalid' commit -m "C1: stable release" \
 && printf 'release=1\nfeature=accidentally-public\n' > /opt/fixtures/stage-git-01/release.txt \
 && git -C /opt/fixtures/stage-git-01 add release.txt \
 && GIT_AUTHOR_DATE='2026-01-01T00:01:00Z' GIT_COMMITTER_DATE='2026-01-01T00:01:00Z' git -C /opt/fixtures/stage-git-01 -c user.name='Developer Dungeon' -c user.email='fixture@example.invalid' commit -m "C2: accidental public change" \
 && git init --initial-branch=feature/notification /opt/fixtures/stage-git-02 \
 && rm -rf /opt/fixtures/stage-git-02/.git/hooks \
 && mkdir -p /opt/fixtures/stage-git-02/.git/hooks \
 && printf 'profile=stable\n' > /opt/fixtures/stage-git-02/profile.txt \
 && git -C /opt/fixtures/stage-git-02 add profile.txt \
 && GIT_AUTHOR_DATE='2026-01-02T00:00:00Z' GIT_COMMITTER_DATE='2026-01-02T00:00:00Z' git -C /opt/fixtures/stage-git-02 -c user.name='Developer Dungeon' -c user.email='fixture@example.invalid' commit -m "C0: shared branch base" \
 && git -C /opt/fixtures/stage-git-02 switch -c feature/profile \
 && printf 'notify=enabled\n' > /opt/fixtures/stage-git-02/notification.txt \
 && git -C /opt/fixtures/stage-git-02 add notification.txt \
 && GIT_AUTHOR_DATE='2026-01-02T00:01:00Z' GIT_COMMITTER_DATE='2026-01-02T00:01:00Z' git -C /opt/fixtures/stage-git-02 -c user.name='Developer Dungeon' -c user.email='fixture@example.invalid' commit -m "C1: notification change on wrong branch" \
 && git init --initial-branch=main /opt/fixtures/stage-git-03 \
 && rm -rf /opt/fixtures/stage-git-03/.git/hooks \
 && mkdir -p /opt/fixtures/stage-git-03/.git/hooks \
 && printf 'search.enabled=false\nsearch.query.minimum=2\nsearch.query.maximum=100\nsearch.sort=relevance\nsearch.page.size=20\nsearch.cache=false\nsearch.locale=ja\nsearch.placeholder=Search\n' > /opt/fixtures/stage-git-03/search.txt \
 && git -C /opt/fixtures/stage-git-03 add search.txt \
 && GIT_AUTHOR_DATE='2026-01-03T00:00:00Z' GIT_COMMITTER_DATE='2026-01-03T00:00:00Z' git -C /opt/fixtures/stage-git-03 -c user.name='Developer Dungeon' -c user.email='fixture@example.invalid' commit -m "C0: search base" \
 && git -C /opt/fixtures/stage-git-03 switch -c feature/search \
 && printf 'search.enabled=false\nsearch.query.minimum=2\nsearch.query.maximum=100\nsearch.sort=relevance\nsearch.page.size=20\nsearch.cache=false\nsearch.locale=ja\nsearch.placeholder=Find products\n' > /opt/fixtures/stage-git-03/search.txt \
 && git -C /opt/fixtures/stage-git-03 add search.txt \
 && GIT_AUTHOR_DATE='2026-01-03T00:01:00Z' GIT_COMMITTER_DATE='2026-01-03T00:01:00Z' git -C /opt/fixtures/stage-git-03 -c user.name='Developer Dungeon' -c user.email='fixture@example.invalid' commit -m "C1: search placeholder" \
 && git -C /opt/fixtures/stage-git-03 switch main \
 && printf 'search.enabled=true\nsearch.query.minimum=2\nsearch.query.maximum=100\nsearch.sort=relevance\nsearch.page.size=20\nsearch.cache=false\nsearch.locale=ja\nsearch.placeholder=Search\n' > /opt/fixtures/stage-git-03/search.txt \
 && git init --initial-branch=main /opt/fixtures/stage-git-04 \
 && rm -rf /opt/fixtures/stage-git-04/.git/hooks \
 && mkdir -p /opt/fixtures/stage-git-04/.git/hooks /opt/fixtures/stage-git-04/src/main/resources \
 && printf 'profile.description=Manage your account.\n' > /opt/fixtures/stage-git-04/src/main/resources/messages.properties \
 && git -C /opt/fixtures/stage-git-04 add src/main/resources/messages.properties \
 && GIT_AUTHOR_DATE='2026-01-04T00:00:00Z' GIT_COMMITTER_DATE='2026-01-04T00:00:00Z' git -C /opt/fixtures/stage-git-04 -c user.name='Developer Dungeon' -c user.email='fixture@example.invalid' commit -m "C0: shared profile message" \
 && git -C /opt/fixtures/stage-git-04 switch -c feature/profile-message \
 && printf 'profile.description=Edit your public profile.\n' > /opt/fixtures/stage-git-04/src/main/resources/messages.properties \
 && git -C /opt/fixtures/stage-git-04 add src/main/resources/messages.properties \
 && GIT_AUTHOR_DATE='2026-01-04T00:02:00Z' GIT_COMMITTER_DATE='2026-01-04T00:02:00Z' git -C /opt/fixtures/stage-git-04 -c user.name='Developer Dungeon' -c user.email='fixture@example.invalid' commit -m "C2: public profile message" \
 && git -C /opt/fixtures/stage-git-04 switch main \
 && printf 'profile.description=Manage security settings.\n' > /opt/fixtures/stage-git-04/src/main/resources/messages.properties \
 && git -C /opt/fixtures/stage-git-04 add src/main/resources/messages.properties \
 && GIT_AUTHOR_DATE='2026-01-04T00:01:00Z' GIT_COMMITTER_DATE='2026-01-04T00:01:00Z' git -C /opt/fixtures/stage-git-04 -c user.name='Developer Dungeon' -c user.email='fixture@example.invalid' commit -m "C1: security settings message" \
 && git init --initial-branch=main /opt/fixtures/stage-git-05 \
 && rm -rf /opt/fixtures/stage-git-05/.git/hooks \
 && mkdir -p /opt/fixtures/stage-git-05/.git/hooks \
 && printf 'retry.enabled=false\nretry.max-attempts=1\n' > /opt/fixtures/stage-git-05/payment-retry.properties \
 && git -C /opt/fixtures/stage-git-05 add payment-retry.properties \
 && GIT_AUTHOR_DATE='2026-01-05T00:00:00Z' GIT_COMMITTER_DATE='2026-01-05T00:00:00Z' git -C /opt/fixtures/stage-git-05 -c user.name='Developer Dungeon' -c user.email='fixture@example.invalid' commit -m "C0: payment retry disabled" \
 && GIT_COMMITTER_DATE='2026-01-05T00:00:30Z' git -C /opt/fixtures/stage-git-05 switch -c feature/payment-retry \
 && printf 'retry.enabled=true\nretry.max-attempts=3\n' > /opt/fixtures/stage-git-05/payment-retry.properties \
 && git -C /opt/fixtures/stage-git-05 add payment-retry.properties \
 && GIT_AUTHOR_DATE='2026-01-05T00:01:00Z' GIT_COMMITTER_DATE='2026-01-05T00:01:00Z' git -C /opt/fixtures/stage-git-05 -c user.name='Developer Dungeon' -c user.email='fixture@example.invalid' commit -m "C1: payment retry" \
 && GIT_COMMITTER_DATE='2026-01-05T00:01:30Z' git -C /opt/fixtures/stage-git-05 switch main \
 && GIT_COMMITTER_DATE='2026-01-05T00:02:00Z' git -C /opt/fixtures/stage-git-05 branch -D feature/payment-retry \
 && chown -R challenge:challenge /opt/fixtures /opt/empty-hooks \
 && chmod -R a-w /opt/fixtures /opt/empty-hooks

COPY rootfs/ /opt/image-rootfs/
RUN chown root:root /opt/image-rootfs/stage-four-file \
 && chmod 0555 /opt/image-rootfs/stage-four-file
USER 10001:10001
WORKDIR /workspace
CMD ["/bin/sleep", "infinity"]
