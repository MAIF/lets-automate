import * as React from 'react';
import {ReactNode} from "react";
import Transition from "react-transition-group/Transition";


export interface FadeProps {
    inProp: boolean;
    duration: number;
    children: ReactNode;
    onEntered: () => void;
}

export const Fade = (props: FadeProps) => {
    const duration = props.duration;
    const defaultStyle = {
        transition: `opacity ${duration}ms ease-in-out`,
        opacity: 1,
    };

    const transitionStyles: any = {
        entering: { opacity: 0 },
        entered:  { opacity: 1 },
    };
    return (
        <Transition in={props.inProp} timeout={duration} onEntered={props.onEntered}>
            {(state: any) => (
                <div style={{
                    ...defaultStyle,
                    ...transitionStyles[state]
                }}>
                    {props.children}
                </div>
            )}
        </Transition>
    );
}
