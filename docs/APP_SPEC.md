# BluePath Final Mobile App Specification Summary

## Service Definition

BluePath is a mobile learning and career platform designed for use by marine education institutions and field-based learning environments. Its goal is to spark users' interest in the ocean and help learners of all ages grow into future marine professionals.

## Core Users

- **Elementary school students:** Explore marine life, Dokdo, ships, and ocean experience programs.
- **Middle and high school students:** Discover marine-related careers, understand occupations, and receive recommendations for hands-on programs.
- **University students and job seekers:** Review marine and fisheries occupations, NCS competencies, certifications, and examination information.
- **Adults and professionals:** Find retraining opportunities, job competency development programs, and advanced professional courses.
- **Parents:** Identify family-oriented and experiential marine education programs suitable for their children.
- **Education institution managers:** Analyze educational demand based on user interest data.

## Core Flow

```text
Onboarding Assessment
→ Marine Talent Type Classification
→ Content and Education Recommendations
→ Quiz-Based Tier Progression
→ Schedule and Event Integration
→ NCS Career Roadmap
→ AI Agent Consultation
```

## Tier System

| Tier | Meaning | Promotion Criteria Overview |
|---|---|---|
| Bronze | Ocean Beginner | Complete 3–5 beginner videos or score at least 70% on a basic quiz |
| Silver | Foundation Learner | Complete 5–7 intermediate learning items and score at least 75% on category-specific quizzes |
| Gold | Career Explorer | Follow an NCS job roadmap and score at least 80% on advanced quizzes |
| Platinum | Practice-Ready Learner | Register or express interest in certifications, exams, or professional training schedules |
| Diamond | Marine Professional | Verify relevant work experience, certificates of completion, professional licenses, or project experience |

## Recommendation Score

```text
Recommendation Score =
Interest Similarity
+ Age and Level Suitability
+ Tier Suitability
+ Career and NCS Relevance
+ Schedule Accessibility
+ User Behavior History
```

## Example AI Agent Commands

- Recommend training programs based on my interests.
- Show me marine events my family can attend this month.
- What should I do to advance to the next tier?
- What competencies do I need to become a navigation officer?
- Let me take a quiz for Gold-tier promotion.
