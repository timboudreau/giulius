create table if not exists things (
    thingid bigserial not null primary key,
    sender smallint,
    ts timestamp default now(),
    name varchar not null
);

insert into things(sender, name) values (23, 'skiddoo');
insert into things(sender, name) values (42, 'meaning');

