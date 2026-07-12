FROM alpine:3.23.3@sha256:59855d3dceb3ae53991193bd03301e082b2a7faa56a514b03527ae0ec2ce3a95

RUN apk add --no-cache git=2.52.0-r0 \
 && addgroup -S -g 10001 challenge \
 && adduser -S -D -H -u 10001 -G challenge challenge \
 && mkdir -p /opt/fixtures/stage-git-01 /opt/empty-hooks \
 && git init --initial-branch=main /opt/fixtures/stage-git-01 \
 && rm -rf /opt/fixtures/stage-git-01/.git/hooks \
 && mkdir -p /opt/fixtures/stage-git-01/.git/hooks \
 && printf 'release=1\n' > /opt/fixtures/stage-git-01/release.txt \
 && git -C /opt/fixtures/stage-git-01 add release.txt \
 && GIT_AUTHOR_DATE='2026-01-01T00:00:00Z' GIT_COMMITTER_DATE='2026-01-01T00:00:00Z' git -C /opt/fixtures/stage-git-01 -c user.name='Developer Dungeon' -c user.email='fixture@example.invalid' commit -m "C1: stable release" \
 && printf 'release=1\nfeature=accidentally-public\n' > /opt/fixtures/stage-git-01/release.txt \
 && git -C /opt/fixtures/stage-git-01 add release.txt \
 && GIT_AUTHOR_DATE='2026-01-01T00:01:00Z' GIT_COMMITTER_DATE='2026-01-01T00:01:00Z' git -C /opt/fixtures/stage-git-01 -c user.name='Developer Dungeon' -c user.email='fixture@example.invalid' commit -m "C2: accidental public change" \
 && chown -R challenge:challenge /opt/fixtures /opt/empty-hooks \
 && chmod -R a-w /opt/fixtures /opt/empty-hooks

COPY rootfs/ /opt/image-rootfs/
USER 10001:10001
WORKDIR /workspace
CMD ["/bin/sleep", "infinity"]
