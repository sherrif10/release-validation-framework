package org.ihtsdo.rvf.dao;

import org.ihtsdo.rvf.entity.Assertion;
import org.ihtsdo.rvf.entity.AssertionTest;
import org.ihtsdo.rvf.entity.Test;

import java.util.List;
import java.util.UUID;

public interface AssertionDao extends EntityDao<Assertion> {

	List<Assertion> findAll();

    AssertionTest getAssertionTests(Long assertionId, Long testId);

    AssertionTest getAssertionTests(UUID uuid, Long testId);

    List<AssertionTest> getAssertionTests(Long assertionId);

    List<AssertionTest> getAssertionTests(UUID uuid);

    AssertionTest getAssertionTests(Assertion assertion, Test test);

    List<AssertionTest> getAssertionTests(Assertion assertion);

    List<Test> getTests(Assertion assertion);

    List<Test> getTests(Long assertionid);

    List<Test> getTests(UUID uuid);
}
