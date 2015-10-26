
/******************************************************************************** 
	file-centric-snapshot-description-double-spaces

	Assertion:
	No active Terms associated with active concepts contain double spaces.

********************************************************************************/
	insert into qa_result (runid, assertionuuid, concept_id, details)
	select 
		<RUNID>,
		'<ASSERTIONUUID>',
		a.conceptid,
		concat('CONCEPT: Term=',a.term, ':Active Terms containing double spaces.') 	
	from curr_description_s a , curr_concept_s b
	where a.active = 1
	and a.conceptid = b.id
	and b.active = 1
	and a.term like '%  %'; 
	commit;