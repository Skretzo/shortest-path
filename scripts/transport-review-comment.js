// Posts / updates the comment linking a PR's push to its transport review on the workbench.
// Run by .github/workflows/transport-review.yml through actions/github-script.
const MARKER = '<!-- transport-review -->';
// The coordinate previews' comment, which this one replaces in place.
const OLD_MARKER = '<!-- osrs-coordinate-preview -->';

module.exports = async ({ github, context }) => {
  const { owner, repo } = context.repo;
  const { number, base, head } = context.payload.pull_request;
  // The link names this push, so it keeps showing these rows after later pushes.
  const page = `https://workbench.collisionlab.world/github/${owner}/${repo}/pull/${number}/files/${base.sha}..${head.sha}`;
  const body = [
    MARKER,
    `**[See this PR's transport changes on the map →](${page})**`,
    `Each changed row is drawn on the planner's map, with its floors, insets and collision. (For ${head.sha.slice(0, 7)}.)`,
  ].join('\n');

  const comments = await github.paginate(github.rest.issues.listComments, { owner, repo, issue_number: number, per_page: 100 });
  const ours = comments.filter(comment => comment.user.type === 'Bot');
  const existing = ours.find(comment => comment.body.includes(MARKER)) ?? ours.find(comment => comment.body.includes(OLD_MARKER));
  if (existing) await github.rest.issues.updateComment({ owner, repo, comment_id: existing.id, body });
  else await github.rest.issues.createComment({ owner, repo, issue_number: number, body });
};
