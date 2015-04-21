
/*  
	The current full simple map refset file consists of the previously published full file and the changes for the current release
*/

/* in the current full; not in the previous full */
	insert into qa_result (runid, assertionuuid, assertiontext, details)
	select 
	<RUNID>,
	'<ASSERTIONUUID>',
	'<ASSERTIONTEXT>',
	concat('SimpleMapRefset: id=',a.id, ' is in current full file, but not in prior full file.') 	
	from curr_simplemaprefset_f a
	left join curr_simplemaprefset_d b
		on a.id = b.id
		and a.effectivetime = b.effectivetime
		and a.active = b.active
    	and a.moduleid = b.moduleid
    	and a.refsetid = b.refsetid
   	 	and a.referencedcomponentid = b.referencedcomponentid
    	and a.maptarget = b.maptarget
    left join prev_simplemaprefset_f c
		on a.id = c.id
		and a.effectivetime = c.effectivetime
		and a.active = c.active
    	and a.moduleid = c.moduleid
    	and a.refsetid = c.refsetid
   	 	and a.referencedcomponentid = c.referencedcomponentid
    	and a.maptarget = c.maptarget
	where ( b.id is null
			or b.effectivetime is null
			or b.active is null
			or b.moduleid is null
  			or b.refsetid is null
  			or b.referencedcomponentid is null
  			or b.maptarget is null)
  		and ( c.id is null
			or c.effectivetime is null
			or c.active is null
			or c.moduleid is null
  			or c.refsetid is null
  			or c.referencedcomponentid is null
  			or c.maptarget is null);

  	
  	/* in the full; not in the delta */
	insert into qa_result (runid, assertionuuid, assertiontext, details)
	select 
		<RUNID>,
		'<ASSERTIONUUID>',
		'<ASSERTIONTEXT>',
		concat('SIMPLE MAP REFSET: id=',a.id, ' is in FULL file, but not in DELTA file.') 
	from curr_simplemaprefset_f a
	left join prev_simplemaprefset_f b
		on a.id = b.id
		and a.effectivetime = b.effectivetime
		and a.active = b.active
    	and a.moduleid = b.moduleid
    	and a.refsetid = b.refsetid
   	 	and a.referencedcomponentid = b.referencedcomponentid
    	and a.maptarget = b.maptarget
    left join curr_simplemaprefset_d  c
		on a.id = c.id
		and a.effectivetime = c.effectivetime
		and a.active = c.active
    	and a.moduleid = c.moduleid
    	and a.refsetid = c.refsetid
   	 	and a.referencedcomponentid = c.referencedcomponentid
    	and a.maptarget = c.maptarget
	where ( b.id is null
			or b.effectivetime is null
			or b.active is null
			or b.moduleid is null
  			or b.refsetid is null
  			or b.referencedcomponentid is null
  			or b.maptarget is null)
  		and ( c.id is null
			or c.effectivetime is null
			or c.active is null
			or c.moduleid is null
  			or c.refsetid is null
  			or c.referencedcomponentid is null
  			or c.maptarget is null);
commit;
