import unittest
from audit_addon_branches import classify

class ClassificationTests(unittest.TestCase):
    def test_identical_commit(self):
        self.assertEqual('same_commit_as_default', classify('a','a',True,True,[]))
    def test_ancestor(self):
        self.assertEqual('ancestor_of_default', classify('a','b',False,True,[]))
    def test_same_tree(self):
        self.assertEqual('same_tree_different_history', classify('a','b',True,False,[]))
    def test_open_pr_takes_priority(self):
        p={'state':'open','merged':False,'head_sha':'a'}
        self.assertEqual('open_pr_preserve', classify('a','a',True,True,[p]))
    def test_merged_exact_tip(self):
        p={'state':'closed','merged':True,'head_sha':'a'}
        self.assertEqual('merged_pr_exact_tip', classify('a','b',False,False,[p]))
    def test_later_branch_commits_are_not_discarded(self):
        p={'state':'closed','merged':True,'head_sha':'old'}
        self.assertEqual('unique_tip_after_closed_pr', classify('new','base',False,False,[p]))
    def test_closed_does_not_mean_completed(self):
        p={'state':'closed','merged':False,'head_sha':'a'}
        self.assertEqual('unique_tip_after_closed_pr', classify('a','b',False,False,[p]))
    def test_no_pr_is_not_completed(self):
        self.assertEqual('unique_tip_no_pr', classify('a','b',False,False,[]))

if __name__=='__main__': unittest.main()
