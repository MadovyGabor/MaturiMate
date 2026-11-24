# Review and refinement procedure

How Milestone 13 ([ROADMAP](ROADMAP.md#milestone-13-review-and-refinement-by-feature-slice)) is run. The work goes slice by slice (see [review-slices.md](review-slices.md)); every slice follows the same nine steps. There are two versions of the steps: **A for slices with screens** and **B for the backend slice** (sync, server, Worker, content download). The decision and its reasons: [DECISIONS](../content/DECISIONS.md) 2026-11-13.

## How to use this file

* Open a slice: work on the milestone's branch `chore/review-and-refinement` (one branch, small commits with the slice in the scope, for example `fix(S3): …`) and start the notes file `llm/outputs/refine-<slice>.md` (local, uncommitted). Everything found in steps 1, 2 and 4 goes into that one list: *where, what, how bad, status*.
* Go through the steps in order. Do not start a step before the one above is done; each step ends with the list updated and a short commit (`fix:`, `refactor:`, `perf:`, `docs:` …).
* A slice is done when step 9 is ticked and its row in `docs/screen-checklist.md` is complete.

## The slices

The list of the slices, in order, with what each holds and why it belongs together, is the owner's checklist [review-slices.md](review-slices.md). This file only says how a slice is run.

---

## Version A: slices with screens

Within a slice the screens are taken in the order of the user's flow. Shared `App…` components are polished in the first slice that uses them and only reused later.

1. **Manual walkthrough (the owner).** Go through every screen of the slice and write down what you find. On every screen try: the normal path; the empty state; the error state (airplane mode, a wrong code); phone, tablet and desktop; light and dark; Hungarian and Slovak; rotating or resizing the window; the back button; the app sent to the background and back. Keep screenshots (light and dark), the design review needs them in step 5. A weak or old phone is part of the walk.
2. **AI code review (Claude).** `/code-review` on the slice's files, bottom-up: data, domain, ViewModel, screen. A fault in a lower layer is fixed before the layers above are touched. The findings are merged with the owner's list.
3. **Fixes.** By severity, in small commits, with a test for every fixed bug.
4. **The owner reads the code,** on the state after the fixes, as the diff of the slice against `main`. Order: the ViewModel (state and events), the domain interface, the repository, the structure of the screen. Three questions for each: is it clear in one reading, is the logic where it belongs, what happens on an error. Notes only; what goes in is decided together. Structure problems are fixed here, before steps 5 to 7 rewrite the code.
5. **UI design review (Claude).** The `ui-design-review:review` skill on the screenshots and the running screens, against `docs/design-system/` and `tokens.json`: hierarchy, spacing, consistency, copy, contrast, touch targets, mobile layout, missing loading, empty and error states. For the key flows (onboarding, sign-up) `ui-design-review:compare` may add a comparison with other apps. The owner picks what counts. *(The UXKIN examples need a sign-in through `/mcp`; without it the review uses platform conventions.)*
6. **UI polish and animations.** The picked problems are fixed (`ui-design-review:polish`): design-system values only, all states, animations, accessibility (screen reader, font scaling, contrast), and the Slovak and Hungarian texts. A changed shared component gets its page in `docs/design-system/components/` updated.
7. **Optimization.** After the polish, because animations and layout changes move the numbers. Measure first, then change: recomposition counts (Layout Inspector), the profiler, database calls, memory, start-up time, scrolling on a weak phone. Only what a measurement justifies.
8. **Final review.** One `/code-review` on the whole diff of the slice, with weight on the changes of steps 3 to 7 (they may bring new faults); a short manual walk again; the checklist in [review-checklist.md](review-checklist.md), group by group.
9. **Close.** The slice's row in `docs/screen-checklist.md` is complete, tests added, coverage still at 80 %, the roadmap ticked, merge.

## Version B: the backend slice (sync, server, Worker, content download)

There is no screen to walk through and no polish; the steps keep their numbers so the two versions can be read side by side. Direction is bottom-up all the way.

1. **Walkthrough by trying it.** Two devices (phone and desktop), online and offline in turns; watch the rows arrive in the Supabase table editor. Cover sign-up, a guest signing in, a reset of the progress, sign-out (also offline), the data export, the account deletion, the content download (also on mobile data) and an exam-date update.
2. **AI code review (Claude), bottom-up:** SQL and RLS, the server functions, the data layer, the engine, the triggers, last the settings rows and the profile badge. The question at every level: *what can a modified client send?*
3. **Fixes.** Safety faults first. A change to the server is a new migration, never an edit of an applied one; the tests of `supabase/tests/` are extended with it.
4. **The owner reads:** the migrations, the RLS policies and `supabase/tests/sync_test.sql` and `rls_test.sql`. The aim is to understand who may read and write what, and what the server checks instead of trusting the phone.
5. **UI design review:** not applicable.
6. **UI polish:** only the texts of the settings rows and the profile badge (Slovak and Hungarian).
7. **Optimization:** `EXPLAIN` on the slower queries, indexes, the size and frequency of the sync payloads, the limits of the Worker and of Supabase for the number of testers.
8. **Final review:** `/code-review` on the diff; a second walk through step 1; the runbooks (`docs/runbooks/`) still match what the system does.
9. **Close.** Tests (including the backup restore test of 12.2 if it is still open), the roadmap ticked, merge.

---

## The checklist

What to look at in the steps 2, 4 and 8, and what good looks like, is in [review-checklist.md](review-checklist.md).
