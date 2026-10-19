#!/usr/bin/env node
// Prints Java (JaCoCo) and TypeScript (V8) line coverage separately and fails
// when either is below the threshold. The tools enforce the same gates; this
// keeps one readable summary for the terminal and the GitHub job summary.
import { appendFileSync, existsSync, readFileSync } from 'node:fs';

const THRESHOLD = Number(process.env.MIN_LINE_COVERAGE ?? 90);
const JAVA_REPORT = 'modules/exelearning-web/build/reports/jacoco/test/jacocoTestReport.xml';
const TS_REPORT = 'build/reports/frontend-coverage/coverage-summary.json';

function javaCoverage() {
    if (!existsSync(JAVA_REPORT)) {
        return null;
    }
    const xml = readFileSync(JAVA_REPORT, 'utf8');
    const counters = [...xml.matchAll(/<counter type="LINE" missed="(\d+)" covered="(\d+)"\/>/g)];
    const report = counters.at(-1);
    if (!report) {
        return null;
    }
    const missed = Number(report[1]);
    const covered = Number(report[2]);
    return { covered, total: missed + covered };
}

function typeScriptCoverage() {
    if (!existsSync(TS_REPORT)) {
        return null;
    }
    const { lines } = JSON.parse(readFileSync(TS_REPORT, 'utf8')).total;
    return { covered: lines.covered, total: lines.total };
}

let failed = false;
const rows = [];
for (const [name, result] of [
    ['Java (JaCoCo)', javaCoverage()],
    ['TypeScript (V8)', typeScriptCoverage()],
]) {
    if (!result) {
        failed = true;
        rows.push(`| ${name} | missing report | ${THRESHOLD}% | FAIL |`);
        continue;
    }
    const percent = (result.covered / result.total) * 100;
    const ok = percent >= THRESHOLD;
    failed ||= !ok;
    rows.push(
        `| ${name} | ${percent.toFixed(2)}% (${result.covered}/${result.total}) | ${THRESHOLD}% | ${ok ? 'PASS' : 'FAIL'} |`,
    );
}

const table = ['| Component | Line coverage | Required | Result |', '| --- | --- | --- | --- |', ...rows].join('\n');
console.log(table);
if (process.env.GITHUB_STEP_SUMMARY) {
    appendFileSync(process.env.GITHUB_STEP_SUMMARY, `## Line coverage\n\n${table}\n`);
}
process.exit(failed ? 1 : 0);
