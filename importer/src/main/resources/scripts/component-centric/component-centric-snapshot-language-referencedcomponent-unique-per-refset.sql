/******************************************************************************** 
	component-centric-snapshot-language-referencedcomponent-unique-per-refset

	Assertion: There is only one member id per description per dialect in the language refset snapshot file.
********************************************************************************/
	
	insert into qa_result (runid, assertionuuid, concept_id, details)
	select 
		<RUNID>,
		'<ASSERTIONUUID>',
		b.conceptid,
		concat('Description: id=',temp.referencedcomponentid, ': has multiple language refset members for a given dialect.') 
	from 
	(select distinct a.refsetid, a.referencedcomponentid from curr_langrefset_d a 
	left join curr_langrefset_s b on a.refsetid =b.refsetid and a.referencedcomponentid=b.referencedcomponentid where a.id != b.id) as temp,
	description_s b 
	where temp.referencedcomponentid =b.id;
	
	
	