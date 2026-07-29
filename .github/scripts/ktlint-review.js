import {existsSync, readFileSync} from 'fs';
import {Octokit} from '@octokit/rest';

const octokit = new Octokit({auth: process.env.GITHUB_TOKEN});
const [owner, repo] = process.env.GITHUB_REPOSITORY.split('/');
const prNumber = parseInt(process.env.PR_NUMBER);

const REPORT_PATH = 'build/ktlint-report.json';

await main();

async function main() {
    if (!existsSync(REPORT_PATH)) {
        console.log('No ktlint report found.');
        return;
    }

    const ktLintIssues = parseJsonReport(REPORT_PATH);
    if (ktLintIssues.length === 0) {
        console.log('No ktlint issues found.');
        return;
    }

    const diffPositionMap = await buildDiffPositionMap();
    const comments = buildReviewComments(ktLintIssues, diffPositionMap);

    if (comments.length === 0) {
        console.log('No ktlint issues fall within the PR diff.');
        return;
    }

    await clearOldKtlintCommentsAndDismissReview();
    await postReview(comments);

    console.error(`ktlint found ${comments.length} issue(s) in the diff.`);
    process.exit(1);
}

function parseJsonReport(path) {
    const report = JSON.parse(readFileSync(path, 'utf8'));
    const ktLintIssues = [];

    for (const item of report) {
        for (const error of item.errors) {
            ktLintIssues.push({
                path: item.file,
                line: error.line,
                message: error.message,
            });
        }
    }

    return ktLintIssues;
}

async function buildDiffPositionMap() {
    const {data: changedFiles} = await octokit.rest.pulls.listFiles({
        owner,
        repo,
        pull_number: prNumber,
        per_page: 100,
    });

    const diffPositionMap = {};
    for (const file of changedFiles) {
        diffPositionMap[file.filename] = parsePatch(file.patch);
    }
    return diffPositionMap;
}

function parsePatch(patch) {
    const lineToPosition = {};
    if (!patch) return lineToPosition;

    let diffPosition = 0;
    let currentLine = 0;

    for (const diffLine of patch.split('\n')) {
        diffPosition++;

        if (diffLine.startsWith('@@')) {
            // "@@ -10,6 +10,8 @@" means the new file side starts at line 10
            const match = diffLine.match(/@@ -\d+(?:,\d+)? \+(\d+)/);
            if (match) currentLine = parseInt(match[1]) - 1;
        } else if (diffLine.startsWith('+')) {
            currentLine++;
            lineToPosition[currentLine] = diffPosition;
        } else if (!diffLine.startsWith('-')) {
            currentLine++;
        }
    }

    return lineToPosition;
}

function buildReviewComments(ktLintIssues, diffPositionMap) {
    const issuesByLine = {};

    for (const error of ktLintIssues) {
        const position = diffPositionMap[error.path]?.[error.line];
        if (position !== undefined) {
            const key = `${error.path}:${position}`;
            if (!issuesByLine[key]) {
                issuesByLine[key] = {
                    path: error.path,
                    position,
                    messages: new Set(),
                };
            }
            issuesByLine[key].messages.add(error.message);
        }
    }

    const comments = [];
    for (const key in issuesByLine) {
        const issueGroup = issuesByLine[key];
        const messages = [...issueGroup.messages];
        const body = messages.length > 1
            ? `**ktlint** issues:\n${messages.map(msg => `- ${msg}`).join('\n')}`
            : `**ktlint**: ${messages[0]}`;

        comments.push({
            path: issueGroup.path,
            position: issueGroup.position,
            body,
        });
    }

    return comments;
}

async function postReview(comments) {
    await octokit.rest.pulls.createReview({
        owner,
        repo,
        pull_number: prNumber,
        event: 'COMMENT',
        body: `**ktlint** found ${comments.length} issue(s) in this PR. Please fix the inline comments below.`,
        comments,
    });
}

async function clearOldKtlintCommentsAndDismissReview() {
    const {data: comments} = await octokit.rest.pulls.listReviewComments({
        owner,
        repo,
        pull_number: prNumber,
        per_page: 100,
    });

    const oldComments = comments.filter(comment => comment.body.startsWith('**ktlint**'));

    if (oldComments.length === 0) {
        console.log('No old ktlint comments found.');
        return;
    }

    console.log(`Found ${oldComments.length} old ktlint comments to delete.`);
    for (const comment of oldComments) {
        await octokit.rest.pulls.deleteReviewComment({
            owner,
            repo,
            comment_id: comment.id,
        });
    }

    const {data: reviews} = await octokit.rest.pulls.listReviews({
        owner,
        repo,
        pull_number: prNumber,
    });

    const oldReview = reviews.find(review => review.body.startsWith('**ktlint**') && review.state !== 'DISMISSED');
    if (oldReview === undefined) {
        console.log('No old ktlint Review found.');
        return;
    }
    await octokit.rest.pulls.dismissReview({
        owner,
        repo,
        pull_number: prNumber,
        review_id: oldReview.id,
        message: 'Dismissed: ktlint issues have been re-evaluated.',
    });
}
