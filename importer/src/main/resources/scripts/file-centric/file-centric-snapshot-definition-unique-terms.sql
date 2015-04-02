
/******************************************************************************** 
	file-centric-snapshot-definition-unique-terms

	Assertion:
	There are no active duplicate Definition terms in the DEFINITION snapshot file.

********************************************************************************/
	
/* 	view of current snapshot made by finding duplicate terms in textdefinition file*/
	drop table if exists v_curr_snapshot;
	create table if not exists  v_curr_snapshot as
	select a.id, a.term 
	from curr_textdefinition_s a
	where active = 1
	group by BINARY  a.term
	having count(a.term) > 1 ;

	
/* 	inserting exceptions in the result table */
	insert into qa_result (runid, assertionuuid, assertiontext, details)
	select 
		<RUNID>,
		'<ASSERTIONUUID>',
		'<ASSERTIONTEXT>',
		concat('Definition id =', a.id,': Term=[',a.term, '] is duplicate in the DEFINITION snapshot file.') 	
	from v_curr_snapshot a;


	drop table if exists v_curr_snapshot;

	