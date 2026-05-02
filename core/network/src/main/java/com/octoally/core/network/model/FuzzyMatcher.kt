package com.octoally.core.network.model

/**
 * Pure-JVM port of `octoally/dashboard/src/lib/fuzzy.ts`.
 *
 * The server does NOT expose a `POST /skill-suggest` endpoint; the dashboard
 * and this client rank skills locally against the pre-computed intent map
 * from `GET /api/skills/intents`. This object provides the two public search
 * entry points used by [SkillSuggestViewModel]:
 *
 *  1. [slashMatch] — used when the user typed `/name…`, matching the skill
 *     id/name/command with a fzf-lite prefix/substring/subsequence score.
 *  2. [intentMatch] — used for free-text queries, scoring each intent phrase
 *     against the query words (exact substring > word overlap > partial
 *     prefix), weighted by the server-supplied [SkillIntent.weight].
 *
 * The source TS uses fuse.js for fuzzy-match scoring; we reimplement the
 * relevant scoring manually because fuse.js has no first-class Kotlin port
 * and the algorithm is simple enough that the fidelity cost of a faithful
 * port is not worth pulling in a dependency. The intent-search scoring is
 * an **exact port** of `searchIntent` in fuzzy.ts; the slash-search scoring
 * is an **adapted port** that preserves the behaviour (prefix match → highest,
 * substring → high, subsequence → graded) without fuse's tolerance for typos.
 */
object FuzzyMatcher {

    /** One ranked candidate returned from [slashMatch] or [intentMatch]. */
    data class ScoredCandidate(
        val skillId: String,
        val score: Double,
        val matchedTerm: String? = null,
    )

    // ── Synonym map (verbatim from fuzzy.ts) ─────────────────────────────────
    private val SYNONYMS: Map<String, List<String>> = mapOf(
        "debug"       to listOf("debugging", "troubleshoot", "fix", "error", "bug", "issue", "trace", "diagnose"),
        "fix"         to listOf("debug", "repair", "patch", "resolve", "bug", "error", "troubleshoot"),
        "test"        to listOf("testing", "spec", "unit", "integration", "e2e", "assert", "verify", "check"),
        "commit"      to listOf("git", "save", "push", "stage", "changes", "version"),
        "review"      to listOf("pr", "pull-request", "code-review", "feedback", "inspect"),
        "deploy"      to listOf("release", "ship", "publish", "launch", "ci", "cd", "pipeline"),
        "build"       to listOf("compile", "bundle", "package", "construct", "assemble"),
        "analyze"     to listOf("analysis", "inspect", "examine", "audit", "evaluate", "assess", "scan"),
        "refactor"    to listOf("restructure", "cleanup", "improve", "reorganize", "simplify"),
        "document"    to listOf("docs", "documentation", "readme", "explain", "describe", "comment"),
        "plan"        to listOf("planning", "design", "architect", "strategy", "roadmap", "spec"),
        "search"      to listOf("find", "lookup", "query", "grep", "locate", "discover"),
        "create"      to listOf("new", "add", "generate", "scaffold", "init", "make", "start"),
        "delete"      to listOf("remove", "drop", "clean", "purge", "destroy"),
        "update"      to listOf("upgrade", "modify", "change", "edit", "patch", "bump"),
        "security"    to listOf("vulnerability", "audit", "secure", "auth", "permission", "access"),
        "performance" to listOf("optimize", "speed", "fast", "slow", "benchmark", "profile", "perf"),
        "memory"      to listOf("store", "recall", "remember", "save", "persist", "cache"),
        "git"         to listOf("branch", "merge", "rebase", "commit", "push", "pull", "stash", "diff"),
        "workflow"    to listOf("automate", "automation", "pipeline", "process", "ci", "cd"),
        "explain"     to listOf("understand", "what", "how", "why", "describe", "clarify"),
        "error"       to listOf("bug", "crash", "fail", "broken", "wrong", "issue", "exception"),
        "brainstorm"  to listOf("idea", "ideate", "think", "explore", "creative", "options"),
        "implement"   to listOf("code", "write", "develop", "build", "create", "program"),
        "estimate"    to listOf("time", "effort", "complexity", "scope", "size", "cost"),
        "hook"        to listOf("hooks", "event", "trigger", "callback", "lifecycle"),
        "session"     to listOf("save", "restore", "state", "context", "history"),
        "index"       to listOf("codebase", "map", "structure", "overview", "navigate"),
        "research"    to listOf("investigate", "explore", "study", "learn", "survey", "deep-dive"),
    )

    /**
     * Expand a skill's searchable text via the synonym map, matching the
     * `expandKeywords` helper from fuzzy.ts.
     */
    internal fun expandKeywords(skill: Skill): String {
        val text = buildString {
            append(skill.id); append(' ')
            append(skill.name); append(' ')
            append(skill.category); append(' ')
            append(skill.description.orEmpty())
        }.lowercase()

        val extra = linkedSetOf<String>()
        for ((trigger, synonyms) in SYNONYMS) {
            if (text.contains(trigger)) {
                extra.addAll(synonyms)
            }
            for (s in synonyms) {
                if (text.contains(s)) {
                    extra.add(trigger)
                    extra.addAll(synonyms)
                    break
                }
            }
        }
        return extra.joinToString(" ")
    }

    /**
     * Slash mode: rank [candidates] (the full skill list) against the leading
     * command query. Returns candidates with score > 0, descending.
     *
     * Scoring (adapted from fuse.js + the legacy [fuzzyScore] helper):
     *   • exact prefix match   -> 200 + q.length
     *   • exact substring      -> 100 + q.length - index
     *   • subsequence match    -> sum of 2*consecutive + 5 bonus at start
     *   • no subsequence       -> skipped
     *
     * Each candidate's best score across its id / name / command wins; weights
     * mirror fuse.js config in fuzzy.ts (id 0.5, name 0.3, command 0.2).
     */
    fun slashMatch(query: String, candidates: List<Skill>): List<ScoredCandidate> {
        if (query.isBlank()) return emptyList()
        val q = query.trim().lowercase()

        val results = mutableListOf<ScoredCandidate>()
        for (skill in candidates) {
            val idScore      = fuzzyScore(q, skill.id) * 0.5
            val nameScore    = fuzzyScore(q, skill.name) * 0.3
            val commandScore = fuzzyScore(q, skill.command.removePrefix("/")) * 0.2
            val best = maxOf(idScore, nameScore, commandScore)
            if (best > 0.0) {
                results += ScoredCandidate(skillId = skill.id, score = best, matchedTerm = null)
            }
        }
        results.sortByDescending { it.score }
        return results
    }

    /**
     * Free-text mode: rank [skills] against [input] using the server-supplied
     * [intents] map. This is an **exact** port of `searchIntent` in fuzzy.ts.
     *
     *   • `input.length < 3` -> empty
     *   • per intent: `intent.includes(q)` > `q.includes(intent)` > word overlap
     *   • final score = bestScore * weight (usage boost is applied by caller)
     */
    fun intentMatch(
        input: String,
        intents: List<SkillIntent>,
        skills: List<Skill>,
    ): List<ScoredCandidate> {
        if (input.length < 3) return emptyList()
        val q = input.lowercase().trim()
        val qWords = q.split(Regex("\\s+")).filter { it.length > 1 }
        if (qWords.isEmpty()) return emptyList()

        // Build a command -> skill.id lookup so callers don't have to.
        val skillIdByCommand = HashMap<String, String>(skills.size)
        for (s in skills) skillIdByCommand[s.command] = s.id

        data class Scored(val entry: SkillIntent, val score: Double, val matched: String)
        val scored = mutableListOf<Scored>()

        for (entry in intents) {
            var bestScore = 0.0
            var bestIntent = ""

            for (intent in entry.intents) {
                val intentLower = intent.lowercase()
                val matchScore: Double = when {
                    intentLower.contains(q) -> 10.0 + q.length
                    q.contains(intentLower) -> 8.0 + intentLower.length
                    else -> {
                        val intentWords = intentLower.split(Regex("\\s+"))
                        var wordMatches = 0
                        var partialMatches = 0
                        for (qw in qWords) {
                            for (iw in intentWords) {
                                if (qw == iw) { wordMatches += 2; break }
                                if (iw.startsWith(qw) || qw.startsWith(iw)) {
                                    partialMatches += 1; break
                                }
                            }
                        }
                        wordMatches + partialMatches * 0.5
                    }
                }

                if (matchScore > bestScore) {
                    bestScore = matchScore
                    bestIntent = intent
                }
            }

            if (bestScore > 0.0) {
                scored += Scored(entry, bestScore * entry.weight, bestIntent)
            }
        }

        scored.sortByDescending { it.score }

        val out = ArrayList<ScoredCandidate>(scored.size)
        for (s in scored) {
            val id = skillIdByCommand[s.entry.command] ?: continue
            out += ScoredCandidate(skillId = id, score = s.score, matchedTerm = s.matched)
        }
        return out
    }

    // ── Internal scoring (port of legacy `fuzzyScore` from fuzzy.ts) ─────────
    //
    // Returns 0.0 for no match so callers can use a single `> 0.0` threshold.
    // The TS version returns -1; we translate that to 0 here for Double-safety.
    internal fun fuzzyScore(query: String, target: String): Double {
        if (query.isEmpty()) return 0.0
        val q = query.lowercase()
        val t = target.lowercase()

        if (t.startsWith(q)) return (200 + q.length).toDouble()
        val subIdx = t.indexOf(q)
        if (subIdx >= 0) return (100 + q.length - subIdx).toDouble()

        var score = 0
        var qi = 0
        var consecutive = 0
        var prevMatch = false
        var ti = 0
        while (ti < t.length && qi < q.length) {
            if (t[ti] == q[qi]) {
                qi++
                consecutive = if (prevMatch) consecutive + 1 else 1
                score += consecutive * 2 + (if (ti == 0) 5 else 0)
                prevMatch = true
            } else {
                prevMatch = false
            }
            ti++
        }
        return if (qi == q.length) score.toDouble() else 0.0
    }
}
