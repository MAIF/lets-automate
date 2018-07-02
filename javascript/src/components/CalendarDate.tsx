
import * as React from 'react';
import {Moment} from "moment";


interface CalendarDateProps {
    date: Moment;
}

export const CalendarDate = (props: CalendarDateProps) => {
    const day: number = props.date.daysInMonth();
    const year: number = props.date.year();
    const month: string = props.date.format("MMMM");
    return <span>{props.date.format("DD MMMM YYYY")}</span>;
    // return (
    //     <time dateTime={props.date.format()} className="icon">
    //         <em>{year}</em>
    //         <strong>{month}</strong>
    //         <span>{day}</span>
    //     </time>
    // );
}

