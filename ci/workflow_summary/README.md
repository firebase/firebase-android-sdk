# `workflow_information.py` Script

## Prerequisites
-   [Python](https://www.python.org/) and required packages.
    ```
    pip install requests argparse
    ```

## Usage
-   Collect last `90` days' `Postsubmit` `ci_workflow.yml` workflow runs:
    ```
    python workflow_information.py --token ${your_github_toke} --branch master --event push --d 90
    ```

-   Collect last `30` days' `Presubmit` `ci_workflow.yml` workflow runs:
    ```
    python workflow_information.py --token ${your_github_toke} --event pull_request --d 30
    ```

-   Please refer to `Inputs` section for more use cases, and `Outputs` section for the workflow summary report format.

## Inputs
-  `-o, --repo_owner`: **[Required]** GitHub repo owner, default value is `firebase`.

-  `-n, --repo_name`: **[Required]** GitHub repo name, default value is `firebase-android-sdk`.

-  `-t, --token`: **[Required]** GitHub access token. See [Creating a personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token).

-  `-w, --workflow_name`: **[Required]** Workflow filename, default value is `ci_tests.yml`.

-  `-d, --days`: Filter workflows that running in past -d days, default value is `90`. See [retention period for GitHub Actions artifacts and logs](https://docs.github.com/en/organizations/managing-organization-settings/configuring-the-retention-period-for-github-actions-artifacts-and-logs-in-your-organization).

-  `-b, --branch`: Filter branch name that workflows run against.

-  `-a, --actor`: Filter the actor who triggers the workflow runs.

-  `-e, --event`: Filter workflows trigger event, could be one of the following values `['push', 'pull_request', 'issue']`.

-  `-j, --jobs`: Filter workflows jobs, default is `all` (including rerun jobs), could be one of the following values `['latest', 'all']`.

-  `-f, --folder`: Workflow and job information will be store here, default value is the current datatime.


## Outputs

-   `workflow_summary_report.txt`: a general report contains workflow pass/failure count, running time, etc.

    ```
    2023-03-03 01:37:07.114500
    Namespace(actor=None, branch=None, days=30, event='pull_request', folder='presubmit_30', jobs='all', repo_name='firebase-android-sdk', repo_owner='firebase', token=${your_github_token}, workflow_name='ci_tests.yml')

    Workflow 'ci_tests.yml' Report: 
     Workflow Failure Rate:64.77% 
     Workflow Total Count: 193 (success: 68, failure: 125)

    Workflow Runtime Report:
    161 workflow runs finished without rerun, the average running time: 0:27:24.745342
    Including:
     56 passed workflow runs, with average running time: 0:17:29.214286
     105 failed workflow runs, with average running time: 0:32:42.361905

    32 runs finished with rerun, the average running time: 1 day, 3:57:53.937500
    The running time for each workflow reruns are:
     ['1 day, 2:24:32', '3:35:54', '3:19:14', '4 days, 6:10:50', '15:33:39', '1:57:21', '1:13:12', '1:55:18', '12 days, 21:51:29', '0:48:48', '0:45:28', '1:40:21', '2 days, 1:46:35', '19:47:16', '0:45:49', '2:22:36', '0:25:22', '0:55:30', '1:40:32', '1:10:05', '20:08:38', '0:31:03', '5 days, 9:19:25', '5:10:44', '1:20:57', '0:28:47', '1:52:44', '20:19:17', '0:35:15', '21:31:07', '3 days, 1:06:44', '3 days, 2:18:14']

    Job Failure Report:
    Unit Tests (:firebase-storage):
     Failure Rate:54.61%
     Total Count: 152 (success: 69, failure: 83)
    Unit Tests (:firebase-messaging):
     Failure Rate:35.37%
     Total Count: 147 (success: 95, failure: 52)
    ```


-   Intermediate file `workflow_summary.json`: contains all the workflow runs and job information attached to each workflow.

    ```
    {
      'workflow_name':'ci_tests.yml',
      'total_count':81,
      'success_count':32,
      'failure_count':49,
      'created':'>2022-11-30T23:15:04Z',
      'workflow_runs':[
        {
          'workflow_id':4296343867,
          'conclusion':'failure',
          'head_branch':'master',
          'actor':'vkryachko',
          'created_at':'2023-02-28T18:47:40Z',
          'updated_at':'2023-02-28T19:20:16Z',
          'run_started_at':'2023-02-28T18:47:40Z',
          'run_attempt':1,
          'html_url':'https://github.com/firebase/firebase-android-sdk/actions/runs/4296343867',
          'jobs_url':'https://api.github.com/repos/firebase/firebase-android-sdk/actions/runs/4296343867/jobs',
          'jobs':{
            'total_count':95,
            'success_count':92,
            'failure_count':3,
            'job_runs':[
              {
                'job_id':11664775180,
                'job_name':'Determine changed modules',
                'conclusion':'success',
                'created_at':'2023-02-28T18:47:42Z',
                'started_at':'2023-02-28T18:47:50Z',
                'completed_at':'2023-02-28T18:50:11Z',
                'run_attempt': 1, 
                'html_url':'https://github.com/firebase/firebase-android-sdk/actions/runs/4296343867/jobs/7487936863',
              }
            ]
          }
        }
      ]
    }
    ```

-   Intermediate file `job_summary.json`: contains all the job runs organized by job name.
    ```
    {
      'Unit Test Results':{   # job name
        'total_count':17,
        'success_count':7,
        'failure_count':10,
        'failure_jobs':[      # data structure is the same as same as workflow_summary['workflow_runs']['job_runs']
          {
            'job_id':11372664143,
            'job_name':'Unit Test Results',
            'conclusion':'failure',
            'created_at':'2023-02-15T22:02:06Z',
            'started_at':'2023-02-15T22:02:06Z',
            'completed_at':'2023-02-15T22:02:06Z',
            'run_attempt': 1, 
            'html_url':'https://github.com/firebase/firebase-android-sdk/runs/11372664143',
          }
        ]
      }
    }
    ```


# `collect_ci_test_logs.py` Script

## Usage
-   Collect `ci_test.yml` job failure logs from `workflow_information.py` script's intermediate file:
    ```
    python collect_ci_test_logs.py --token ${github_toke} --folder ${folder}
    ```

## Inputs

-  `-t, --token`: **[Required]** GitHub access token. See [Creating a personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/creating-a-personal-access-token).

-  `-f, --folder`: **[Required]** Folder that store intermediate files generated by `workflow_information.py`. `ci_workflow.yml` job failure logs will also be stored here.

## Outputs

-   `${job name}.log`: contains job failure rate, list all failed job links and failure logs.
    ```
    Unit Tests (:firebase-storage):
    Failure rate:40.00% 
    Total count: 20 (success: 12, failure: 8)
    Failed jobs:

    https://github.com/firebase/firebase-android-sdk/actions/runs/4296343867/jobs/7487989874
    firebase-storage:testDebugUnitTest
    Task :firebase-storage:testDebugUnitTest
    2023-02-28T18:54:38.1333725Z 
    2023-02-28T18:54:38.1334278Z com.google.firebase.storage.DownloadTest > streamDownloadWithResumeAndCancel FAILED
    2023-02-28T18:54:38.1334918Z     org.junit.ComparisonFailure at DownloadTest.java:190
    2023-02-28T18:57:20.3329130Z 
    2023-02-28T18:57:20.3330165Z 112 tests completed, 1 failed
    2023-02-28T18:57:20.5329189Z 
    2023-02-28T18:57:20.5330505Z > Task :firebase-storage:testDebugUnitTest FAILED
    ```
