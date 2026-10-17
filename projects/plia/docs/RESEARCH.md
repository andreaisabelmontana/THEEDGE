# Research and design rationale

Research reviewed **17 October 2026**. These external studies inform design choices; **none evaluates Plia**. They do not establish a personal origin story, implemented feature, product metric or learning gain. Features described as implications below may remain roadmap work; see [status](STATUS.md).

The proposed learning loop is read, make a note in your own words, retrieve without looking, apply, check and revisit. “Evidence over affirmation” means distinguishing familiarity, confidence and assisted performance from what a learner can recall, explain, correct and use independently.

## Retrieval: confidence is not retention

Roediger and Karpicke (2006) compared restudy and free recall of prose. Restudy helped more at five minutes, while prior testing produced better retention after two days or one week. Restudy also increased confidence despite weaker delayed performance. This motivates retrieval before revealing notes and evaluation after a delay. It does not establish that every quiz format improves every kind of understanding. [Primary paper](https://doi.org/10.1111/j.1467-9280.2006.01693.x), [abstract](https://pubmed.ncbi.nlm.nih.gov/16507066/).

**Plia implication:** distinguish an initial independent attempt from viewing or reproducing a solution. A future note-recall flow should ask for an answer before disclosure.

## Spacing: the retention horizon matters

Cepeda and colleagues (2008) taught facts to more than 1,350 participants, varied review gaps up to 3.5 months and tested retention up to a year later. Increasing the gap initially helped and later hurt; the best gap depended on when the final test occurred. This supports review over time, not a universally optimal interval. Fact retention is also not equivalent to mastery of every complex skill. [Authors' paper](https://laplab.ucsd.edu/articles/Cepeda%20et%20al%202008_psychsci.pdf), [institutional record](https://escholarship.org/uc/item/0kp5q19x).

**Plia implication:** a future scheduler should use a learner's retrieval history and intended retention horizon. Spaced repetition is not implemented in 0.1.23.

## Adaptive review: use observed learning history

Lindsey and colleagues (2014) embedded personalized retrieval review in a semester-long middle-school foreign-language course. With review time matched, later cumulative retention improved by 16.5% relative to massed study and 10.0% relative to generic spaced review. These are relative improvements, not percentage-point gains. The result concerns a specific population, course and scheduler. [Primary paper](https://doi.org/10.1177/0956797613504302), [authors' PDF](https://laplab.ucsd.edu/articles/LindseyShroyerPashlerMozer2014.pdf).

**Plia implication:** adapt at the item or concept level using observed answers and review history. These study percentages must never become Plia performance claims.

## Interleaving: practice choosing a method

Rohrer, Dedrick, Hartwig and Cheung (2020; first online 2019) randomized 54 seventh-grade mathematics classes to interleaved or blocked practice over four months. An unannounced test one month after a common review favored interleaving: 61% versus 38%, with an effect size of 0.83. This is evidence from mathematics, not a rule that all practice must always be mixed. Learners can still need initial instruction and focused practice. [Primary paper](https://doi.org/10.1037/edu0000367), [US Department of Education study record](https://ies.ed.gov/use-work/awards/efficacy-study-interleaved-mathematics-practice).

**Plia implication:** eventually mix suitable problem types so the learner must select a strategy, not merely repeat a known procedure.

## Feedback: correct errors and uncertainty

Butler, Karpicke and Roediger (2008) found in two experiments that feedback improved retention of wrong answers and correct answers given with low confidence. It doubled retention of the latter compared with no feedback. The experiments do not validate automated AI grading; explanations and answer keys still need accuracy checks. [Primary paper](https://doi.org/10.1037/0278-7393.34.4.918), [abstract](https://pubmed.ncbi.nlm.nih.gov/18605878/).

**Plia implication:** provide a specific correction, collect confidence separately and revisit uncertain correct answers. Empty praise is not a useful substitute for information about the work.

## Transfer: apply ideas to changed examples

Butler and colleagues (2018) used geological-science lectures in four experiments. Practicing application with varied examples improved transfer to new application questions two days later compared with repeatedly practicing the same example. This is transfer within the studied tasks, not proof of general intelligence, lifelong adaptability or workplace resilience. [Primary paper](https://doi.org/10.1037/xap0000142), [abstract](https://pubmed.ncbi.nlm.nih.gov/29265856/).

**Plia implication:** evaluate unfamiliar examples separately from recalling the original answer. Deterministic variants are a starting point; broader context changes still require design and evaluation.

## Hebbian learning: inspiration with a boundary

“Neurons that fire together wire together” summarizes a broad idea of activity-dependent plasticity. Bi and Poo (1998), working with cultured rat hippocampal neurons, found that correlated spikes could strengthen or weaken synapses depending on relative timing; initial strength and cell type also mattered. Millisecond timing in cultured neurons does not directly prescribe a human study schedule. [Primary paper](https://doi.org/10.1523/JNEUROSCI.18-24-10464.1998), [full text](https://pmc.ncbi.nlm.nih.gov/articles/PMC6793365/).

**Plia implication:** repeatedly activate and connect ideas through practice, while grounding concrete interface decisions in human learning studies. Plia does not measure synaptic strength, implement a biologically validated algorithm or claim that it “rewires the brain.” The slogan is not a complete theory of learning.

## E-ink: an initial medium, not an efficacy claim

E Ink describes its displays as reflective and bistable: an image can remain without continuous display power. Whole-device consumption still includes processors, networking, refreshes and optional front lighting. These are display characteristics, not independent evidence of better learning. [Manufacturer's technical description](https://www.eink.com/tech/detail/Benefits).

Benedetto and colleagues (2013) compared print, a Kindle Paperwhite and a Kindle Fire HD in 12 adults reading for an average of 73 minutes per session. Subjective fatigue and blink measures favored e-ink/print over that LCD device; reading speed did not differ significantly. A small, device-specific study cannot establish universal protection from eye strain or improved retention. [Primary paper](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0083676).

Kretzschmar and colleagues (2013) compared print, an e-reader and a tablet in 36 younger and 21 older adults. Comprehension did not differ across media. Younger adults had similar eye-tracking/EEG measures; older adults showed lower measured effort on the tablet. The finding cautions against declaring LCD cognitively inferior. [Primary paper](https://journals.plos.org/plosone/article?id=10.1371/journal.pone.0056178).

**Plia implication:** stable pages and little motion suit the intended reading/writing ritual. Offer readable typography and input alternatives; test fatigue, accessibility, ghosting and latency on actual devices. Reduced distraction is a design choice, not an automatic property of e-ink. Preserve the learning model independently of the display technology.

## AI: assisted success and independent learning can diverge

Bastani and colleagues (2025) studied nearly 1,000 high-school mathematics students in Turkey. A standard GPT-4 interface improved assisted practice scores but reduced later unassisted exam performance by 17% relative to control, or 5.4 percentage points. A learning-oriented tutor largely removed that harm, but did not produce a statistically significant unassisted-exam improvement over control. This was a four-session study in one school with specific interfaces; it does not establish that all AI use harms learning. [Primary paper](https://www.pnas.org/doi/10.1073/pnas.2422633122).

Kestin and colleagues (2025) studied 194 eligible undergraduate physics students in a randomized experiment. A custom tutor built around pedagogical principles produced higher immediate post-test scores than the compared active-learning lessons. Median tutor time was 49 minutes versus an assumed 60 minutes in class. The short intervention and immediate testing do not establish long-term retention or superiority for every tutor, discipline or learner. [Primary paper](https://www.nature.com/articles/s41598-025-97652-6).

**Plia implication:** AI help should be deliberately structured and evaluated. Preserve an initial learner attempt, offer graduated help and later test without assistance. Keep “completed with help” distinct from “can do independently.” More available answers make this distinction more important, not less.

## Handwriting: preserve the thinking, not a medium myth

Urry and colleagues (2021) directly replicated a prominent handwriting-versus-laptop experiment with 142 participants and analyzed similar studies. Their results did not support the claim that longhand reliably improves immediate learning through better encoding. Immediate quizzes do not settle every question about drawing, later review or accessibility. [Primary paper](https://doi.org/10.1177/0956797620965541), [publisher abstract](https://www.psychologicalscience.org/journals/psychological-science/0956797620965541/).

**Plia implication:** support writing and sketching where useful while keeping typing available. Explaining, organizing and retrieving ideas matter more than assuming a stylus guarantees comprehension.

## Adaptability and resilience

Adaptability is the intended ability to apply an idea when a question or context changes. Varied application practice and transfer tasks provide a concrete way to examine it. Resilience is an experience-design ambition: make mistakes recoverable, corrections specific and restarting manageable. Neither is a measured Plia outcome. The defensible wording is “designed to support persistence and revision,” not “proven to build resilience.”

## Evaluation commitments

- Compare matched content and study time against a realistic alternative, recording prior knowledge.
- Measure delayed unassisted recall and new application questions, beyond completion, confidence or assisted scores.
- Record correctness, confidence, hint use and solution exposure separately.
- Evaluate accessibility, fatigue and input/refresh behavior on e-ink and an accessible alternative.
- Audit AI answers, grading and citations for errors.
- Report sample size, uncertainty, null findings and remaining hypotheses.

The literature supports questions worth testing. It cannot substitute for a Plia evaluation or for authentic project history.
